import type { AppBuilderField, AppBuilderFormSchema } from "./types";
import { cloneSerializable } from "./clone";

export function createInitialModel(fields: AppBuilderField[]) {
  return fields.reduce<Record<string, unknown>>((model, field) => {
    model[field.field] = cloneSerializable(
      field.defaultValue ?? getDefaultValue(field)
    );
    return model;
  }, {});
}

export function createRules(fields: AppBuilderField[]) {
  return fields.reduce<Record<string, Array<Record<string, unknown>>>>(
    (rules, field) => {
      if (!field.required) {
        return rules;
      }

      rules[field.field] = [
        {
          required: true,
          message: `${field.label}不能为空`,
          trigger: [
            "select",
            "radio",
            "checkbox",
            "date",
            "daterange",
            "time",
          ].includes(field.type)
            ? "change"
            : "blur",
        },
      ];
      return rules;
    },
    {}
  );
}

export function getDefaultValue(field: AppBuilderField) {
  if (
    field.type === "checkbox" ||
    field.type === "daterange" ||
    field.type === "upload"
  ) {
    return [];
  }

  if (["number", "slider", "rate"].includes(field.type)) {
    return 0;
  }

  if (field.type === "switch") {
    return false;
  }

  return "";
}

export function generateVueCode(schema: AppBuilderFormSchema) {
  const escapedSchema = JSON.stringify(schema, null, 2);

  return `<template>
  <FormRenderer :schema="schema" @submit="handleSubmit" />
</template>

<script setup lang="ts">
import FormRenderer from "@/views/app-builder/form/renderer.vue";

const schema = ${escapedSchema};

function handleSubmit(model: Record<string, unknown>) {
  console.log("submit", model);
}
</script>
`;
}

export function downloadText(filename: string, content: string) {
  const blob = new Blob([content], { type: "text/plain;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
