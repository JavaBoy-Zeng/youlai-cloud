<template>
  <el-form
    ref="formRef"
    :model="model"
    :rules="rules"
    :label-width="`${schema.config.labelWidth}px`"
    :label-position="schema.config.labelPosition"
    :size="schema.config.size"
    class="app-builder-renderer"
  >
    <el-row :gutter="schema.config.gutter">
      <el-col v-for="field in schema.fields" :key="field.id" :span="field.span">
        <el-form-item :label="field.label" :prop="field.field">
          <el-input
            v-if="field.type === 'input'"
            :model-value="getTextValue(field)"
            @update:model-value="setValue(field, $event)"
            :placeholder="field.placeholder"
            :clearable="field.clearable"
            :disabled="isFieldDisabled(field)"
          />

          <el-input
            v-else-if="field.type === 'textarea'"
            :model-value="getTextValue(field)"
            @update:model-value="setValue(field, $event)"
            type="textarea"
            :rows="field.rows"
            :placeholder="field.placeholder"
            :disabled="isFieldDisabled(field)"
          />

          <el-input
            v-else-if="field.type === 'password'"
            :model-value="getTextValue(field)"
            @update:model-value="setValue(field, $event)"
            type="password"
            show-password
            :placeholder="field.placeholder"
            :clearable="field.clearable"
            :disabled="isFieldDisabled(field)"
          />

          <el-input-number
            v-else-if="field.type === 'number'"
            :model-value="getNumberValue(field)"
            @update:model-value="setValue(field, $event)"
            :min="field.min"
            :max="field.max"
            :step="field.step"
            :disabled="isFieldDisabled(field)"
          />

          <el-select
            v-else-if="field.type === 'select'"
            :model-value="getValue(field)"
            @update:model-value="setValue(field, $event)"
            :placeholder="field.placeholder"
            :clearable="field.clearable"
            :multiple="field.multiple"
            :disabled="isFieldDisabled(field)"
            class="full-width"
          >
            <el-option
              v-for="option in field.options"
              :key="String(option.value)"
              :label="option.label"
              :value="option.value"
            />
          </el-select>

          <el-radio-group
            v-else-if="field.type === 'radio'"
            :model-value="getRadioValue(field)"
            @update:model-value="setValue(field, $event)"
            :disabled="isFieldDisabled(field)"
          >
            <el-radio
              v-for="option in field.options"
              :key="String(option.value)"
              :label="option.value"
            >
              {{ option.label }}
            </el-radio>
          </el-radio-group>

          <el-checkbox-group
            v-else-if="field.type === 'checkbox'"
            :model-value="getArrayValue(field)"
            @update:model-value="setValue(field, $event)"
            :disabled="isFieldDisabled(field)"
          >
            <el-checkbox
              v-for="option in field.options"
              :key="String(option.value)"
              :label="option.value"
            >
              {{ option.label }}
            </el-checkbox>
          </el-checkbox-group>

          <el-switch
            v-else-if="field.type === 'switch'"
            :model-value="getBooleanValue(field)"
            @update:model-value="setValue(field, $event)"
            :disabled="isFieldDisabled(field)"
          />

          <el-slider
            v-else-if="field.type === 'slider'"
            :model-value="getNumberValue(field)"
            @update:model-value="setValue(field, $event)"
            :min="field.min"
            :max="field.max"
            :step="field.step"
            :disabled="isFieldDisabled(field)"
          />

          <el-date-picker
            v-else-if="field.type === 'date' || field.type === 'daterange'"
            :model-value="getDateValue(field)"
            :type="getDateType(field)"
            @update:model-value="setValue(field, $event)"
            :value-format="field.valueFormat"
            :placeholder="field.placeholder"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            class="full-width"
            :disabled="isFieldDisabled(field)"
          />

          <el-time-picker
            v-else-if="field.type === 'time'"
            :model-value="getDateValue(field)"
            @update:model-value="setValue(field, $event)"
            :value-format="field.valueFormat"
            :placeholder="field.placeholder"
            class="full-width"
            :disabled="isFieldDisabled(field)"
          />

          <el-rate
            v-else-if="field.type === 'rate'"
            :model-value="getNumberValue(field)"
            @update:model-value="setValue(field, $event)"
            :max="field.max"
            :disabled="isFieldDisabled(field)"
          />

          <el-color-picker
            v-else-if="field.type === 'color'"
            :model-value="getStringValue(field)"
            @update:model-value="setValue(field, $event)"
            :disabled="isFieldDisabled(field)"
          />

          <el-upload
            v-else-if="field.type === 'upload'"
            :file-list="getUploadValue(field)"
            @update:file-list="setValue(field, $event)"
            :action="field.action"
            :accept="field.accept"
            :disabled="isFieldDisabled(field)"
          >
            <el-button type="primary">点击上传</el-button>
          </el-upload>
        </el-form-item>
      </el-col>
    </el-row>

    <el-form-item v-if="schema.config.showButtons && !readonly">
      <el-button type="primary" @click="submitForm">{{
        schema.config.submitText
      }}</el-button>
      <el-button @click="resetForm">{{ schema.config.resetText }}</el-button>
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import type { FormInstance } from "element-plus";
import type { UploadUserFile } from "element-plus";
import { reactive, ref, watch } from "vue";
import { createInitialModel, createRules } from "./generator";
import { cloneSerializable } from "./clone";
import type { AppBuilderField, AppBuilderFormSchema } from "./types";

type DatePickerType =
  | "year"
  | "years"
  | "month"
  | "months"
  | "date"
  | "dates"
  | "datetime"
  | "week"
  | "datetimerange"
  | "daterange"
  | "monthrange"
  | "yearrange";

const props = defineProps<{
  schema: AppBuilderFormSchema;
  initialModel?: Record<string, unknown>;
  readonly?: boolean;
}>();

const emit = defineEmits<{
  submit: [model: Record<string, unknown>];
}>();

const formRef = ref<FormInstance>();
const model = reactive<Record<string, unknown>>(
  createInitialModel(props.schema.fields)
);
const rules = ref(createRules(props.schema.fields));

applyInitialModel(props.initialModel);

watch(
  () => props.schema.fields,
  (fields) => {
    const nextModel = createInitialModel(fields);
    Object.keys(model).forEach((key) => delete model[key]);
    Object.assign(model, nextModel);
    applyInitialModel(props.initialModel);
    rules.value = createRules(fields);
  },
  { deep: true }
);

watch(
  () => props.initialModel,
  (value) => {
    const nextModel = createInitialModel(props.schema.fields);
    Object.keys(model).forEach((key) => delete model[key]);
    Object.assign(model, nextModel);
    applyInitialModel(value);
  },
  { deep: true }
);

async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (valid) {
    emit("submit", cloneSerializable(model));
  }
}

async function validateAndGetModel() {
  const valid = await formRef.value?.validate().catch(() => false);
  return valid ? cloneSerializable(model) : undefined;
}

function resetForm() {
  formRef.value?.resetFields();
}

function applyInitialModel(value?: Record<string, unknown>) {
  if (value) {
    Object.assign(model, cloneSerializable(value));
  }
}

function getValue(
  field: AppBuilderField
): string | number | Record<string, unknown> | unknown[] | undefined {
  return model[field.field] as
    | string
    | number
    | Record<string, unknown>
    | unknown[]
    | undefined;
}

function getTextValue(field: AppBuilderField): string | number | undefined {
  const value = model[field.field];
  return typeof value === "string" || typeof value === "number"
    ? value
    : undefined;
}

function getStringValue(field: AppBuilderField): string {
  return String(model[field.field] ?? "");
}

function getNumberValue(field: AppBuilderField): number {
  const value = model[field.field];
  return typeof value === "number" ? value : 0;
}

function getBooleanValue(field: AppBuilderField): boolean {
  return Boolean(model[field.field]);
}

function getArrayValue(field: AppBuilderField): Array<string | number> {
  return Array.isArray(model[field.field])
    ? (model[field.field] as Array<string | number>)
    : [];
}

function getDateValue(
  field: AppBuilderField
): string | number | Date | string[] | number[] | Date[] | undefined {
  return model[field.field] as
    | string
    | number
    | Date
    | string[]
    | number[]
    | Date[]
    | undefined;
}

function getRadioValue(
  field: AppBuilderField
): string | number | boolean | undefined {
  return model[field.field] as string | number | boolean | undefined;
}

function getUploadValue(field: AppBuilderField): UploadUserFile[] {
  return Array.isArray(model[field.field])
    ? (model[field.field] as UploadUserFile[])
    : [];
}

function getDateType(field: AppBuilderField): DatePickerType {
  return (field.dateType ||
    (field.type === "daterange" ? "daterange" : "date")) as DatePickerType;
}

function setValue(field: AppBuilderField, value: unknown) {
  model[field.field] = value;
}

function isFieldDisabled(field: AppBuilderField) {
  return props.readonly || field.disabled;
}

defineExpose({
  validateAndGetModel,
  submitForm,
});
</script>

<style scoped>
.app-builder-renderer {
  width: 100%;
}

.full-width {
  width: 100%;
}
</style>
