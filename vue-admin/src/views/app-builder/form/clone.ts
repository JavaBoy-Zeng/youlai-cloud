import { toRaw } from "vue";

export function cloneSerializable<T>(value: T): T {
  const rawValue = toRaw(value);
  if (rawValue === undefined || rawValue === null) {
    return rawValue as T;
  }

  return JSON.parse(JSON.stringify(rawValue)) as T;
}
