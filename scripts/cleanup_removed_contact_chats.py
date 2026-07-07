#!/usr/bin/env python3
"""
Clean local chat data for contacts that have been removed or are no longer active.

The script is intentionally conservative:
  - It runs in dry-run mode by default.
  - Add --execute to actually change files or a SQLite database.
  - File cleanup moves matched chat folders/files to a timestamped backup unless
    --hard-delete is also provided.
  - SQLite cleanup creates a full database backup before deleting rows.

Examples:
  # Preview chat folders under ./ChatFiles that are not in active_contacts.txt
  python3 scripts/cleanup_removed_contact_chats.py \
    --chat-root ./ChatFiles \
    --active-contacts ./active_contacts.txt

  # Move orphan chat folders to a backup directory
  python3 scripts/cleanup_removed_contact_chats.py \
    --chat-root ./ChatFiles \
    --active-contacts ./active_contacts.txt \
    --execute

  # Delete rows for explicitly removed contacts from two SQLite tables
  python3 scripts/cleanup_removed_contact_chats.py \
    --removed-contacts ./removed_contacts.txt \
    --sqlite-db ./chat.db \
    --sqlite-target message:contact_id \
    --sqlite-target conversation:contact_id \
    --execute
"""

from __future__ import annotations

import argparse
import csv
import re
import shutil
import sqlite3
import sys
from datetime import datetime
from pathlib import Path
from typing import Iterable


SQLITE_IDENTIFIER = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Delete or move local chat data for removed contacts. By default the "
            "script only previews the cleanup plan."
        )
    )
    contact_group = parser.add_mutually_exclusive_group(required=True)
    contact_group.add_argument(
        "--removed-contacts",
        type=Path,
        help="Text/CSV file containing contacts that should be cleaned.",
    )
    contact_group.add_argument(
        "--active-contacts",
        type=Path,
        help=(
            "Text/CSV file containing contacts that should be kept. In file mode, "
            "entries under --chat-root that are not in this file are cleaned."
        ),
    )

    parser.add_argument(
        "--chat-root",
        type=Path,
        help=(
            "Directory containing one chat folder/file per contact. Each direct "
            "child name is treated as a contact id."
        ),
    )
    parser.add_argument(
        "--include-files",
        action="store_true",
        help="Also clean direct files under --chat-root, not only directories.",
    )
    parser.add_argument(
        "--backup-dir",
        type=Path,
        help="Directory used when moving files instead of hard deleting them.",
    )
    parser.add_argument(
        "--hard-delete",
        action="store_true",
        help="Permanently delete chat folders/files instead of moving them to backup.",
    )

    parser.add_argument(
        "--sqlite-db",
        type=Path,
        help="SQLite database file that stores chat records.",
    )
    parser.add_argument(
        "--sqlite-target",
        action="append",
        default=[],
        metavar="TABLE:CONTACT_COLUMN",
        help=(
            "SQLite table and contact column to clean, for example "
            "message:contact_id. Can be repeated."
        ),
    )

    parser.add_argument(
        "--execute",
        action="store_true",
        help="Actually perform cleanup. Without this flag the script only previews.",
    )
    return parser.parse_args()


def load_contact_ids(path: Path) -> set[str]:
    if not path.exists():
        raise FileNotFoundError(f"Contact file does not exist: {path}")

    ids: set[str] = set()
    if path.suffix.lower() == ".csv":
        with path.open("r", encoding="utf-8-sig", newline="") as file:
            reader = csv.reader(file)
            for row in reader:
                if not row:
                    continue
                value = row[0].strip()
                if value and not value.startswith("#"):
                    ids.add(value)
    else:
        with path.open("r", encoding="utf-8-sig") as file:
            for line in file:
                value = line.strip()
                if value and not value.startswith("#"):
                    ids.add(value)
    return ids


def safe_child_name(path: Path) -> str:
    return path.name.rstrip("/\\")


def list_chat_entries(chat_root: Path, include_files: bool) -> list[Path]:
    if not chat_root.exists() or not chat_root.is_dir():
        raise NotADirectoryError(f"--chat-root is not a directory: {chat_root}")

    entries: list[Path] = []
    for child in sorted(chat_root.iterdir(), key=lambda p: p.name):
        if child.name.startswith("."):
            continue
        if child.is_dir() or (include_files and child.is_file()):
            entries.append(child)
    return entries


def choose_file_cleanup_targets(
    chat_root: Path,
    active_contacts: set[str] | None,
    removed_contacts: set[str] | None,
    include_files: bool,
) -> list[Path]:
    entries = list_chat_entries(chat_root, include_files)
    targets: list[Path] = []

    for entry in entries:
        contact_id = safe_child_name(entry)
        if removed_contacts is not None and contact_id in removed_contacts:
            targets.append(entry)
        elif active_contacts is not None and contact_id not in active_contacts:
            targets.append(entry)
    return targets


def default_backup_dir(base: Path) -> Path:
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    return base / ".chat_cleanup_backup" / timestamp


def move_or_delete_paths(
    targets: Iterable[Path],
    backup_dir: Path,
    hard_delete: bool,
    execute: bool,
) -> None:
    target_list = list(targets)
    if not target_list:
        print("[file] No chat folders/files matched cleanup rules.")
        return

    action = "DELETE" if hard_delete else f"MOVE -> {backup_dir}"
    print(f"[file] Matched {len(target_list)} chat folders/files. Action: {action}")
    for target in target_list:
        print(f"  - {target}")

    if not execute:
        print("[file] Dry-run only. Add --execute to apply file cleanup.")
        return

    if not hard_delete:
        backup_dir.mkdir(parents=True, exist_ok=True)

    for target in target_list:
        if hard_delete:
            if target.is_dir():
                shutil.rmtree(target)
            else:
                target.unlink()
        else:
            destination = backup_dir / target.name
            if destination.exists():
                suffix = datetime.now().strftime("%H%M%S_%f")
                destination = backup_dir / f"{target.name}.{suffix}"
            shutil.move(str(target), str(destination))
    print("[file] Cleanup complete.")


def parse_sqlite_target(raw: str) -> tuple[str, str]:
    if ":" not in raw:
        raise ValueError(f"Invalid --sqlite-target '{raw}', expected TABLE:COLUMN")
    table, column = (part.strip() for part in raw.split(":", 1))
    if not SQLITE_IDENTIFIER.match(table):
        raise ValueError(f"Unsafe SQLite table name: {table}")
    if not SQLITE_IDENTIFIER.match(column):
        raise ValueError(f"Unsafe SQLite column name: {column}")
    return table, column


def backup_sqlite_db(db_path: Path) -> Path:
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_path = db_path.with_suffix(db_path.suffix + f".bak_{timestamp}")
    shutil.copy2(db_path, backup_path)
    return backup_path


def cleanup_sqlite(
    db_path: Path,
    sqlite_targets: list[str],
    removed_contacts: set[str],
    execute: bool,
) -> None:
    if not sqlite_targets:
        raise ValueError("--sqlite-db requires at least one --sqlite-target")
    if not db_path.exists() or not db_path.is_file():
        raise FileNotFoundError(f"SQLite database does not exist: {db_path}")
    if not removed_contacts:
        print("[sqlite] Removed contact list is empty; nothing to clean.")
        return

    parsed_targets = [parse_sqlite_target(target) for target in sqlite_targets]
    placeholders = ",".join("?" for _ in removed_contacts)
    contact_values = sorted(removed_contacts)

    with sqlite3.connect(db_path) as conn:
        total = 0
        for table, column in parsed_targets:
            sql = f'SELECT COUNT(*) FROM "{table}" WHERE "{column}" IN ({placeholders})'
            count = conn.execute(sql, contact_values).fetchone()[0]
            print(f"[sqlite] {table}.{column}: {count} rows matched.")
            total += count

        if not execute:
            print("[sqlite] Dry-run only. Add --execute to apply SQLite cleanup.")
            return

        backup_path = backup_sqlite_db(db_path)
        print(f"[sqlite] Database backup created: {backup_path}")

        try:
            conn.execute("BEGIN")
            for table, column in parsed_targets:
                sql = f'DELETE FROM "{table}" WHERE "{column}" IN ({placeholders})'
                conn.execute(sql, contact_values)
            conn.commit()
        except Exception:
            conn.rollback()
            raise

    print(f"[sqlite] Cleanup complete. Deleted up to {total} matched rows.")


def main() -> int:
    args = parse_args()
    contact_file = args.removed_contacts or args.active_contacts
    contacts = load_contact_ids(contact_file)

    if not contacts:
        print(f"No contact ids found in {contact_file}; aborting.", file=sys.stderr)
        return 2

    print(f"Loaded {len(contacts)} contact ids from {contact_file}")

    did_something = False
    if args.chat_root:
        active_contacts = contacts if args.active_contacts else None
        removed_contacts = contacts if args.removed_contacts else None
        targets = choose_file_cleanup_targets(
            args.chat_root,
            active_contacts=active_contacts,
            removed_contacts=removed_contacts,
            include_files=args.include_files,
        )
        backup_dir = args.backup_dir or default_backup_dir(args.chat_root)
        move_or_delete_paths(
            targets,
            backup_dir=backup_dir,
            hard_delete=args.hard_delete,
            execute=args.execute,
        )
        did_something = True

    if args.sqlite_db:
        if args.active_contacts:
            print(
                "[sqlite] --active-contacts cannot be used to infer deleted rows. "
                "Use --removed-contacts for SQLite cleanup.",
                file=sys.stderr,
            )
            return 2
        cleanup_sqlite(
            args.sqlite_db,
            sqlite_targets=args.sqlite_target,
            removed_contacts=contacts,
            execute=args.execute,
        )
        did_something = True

    if not did_something:
        print("Nothing to do. Provide --chat-root and/or --sqlite-db.", file=sys.stderr)
        return 2

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
