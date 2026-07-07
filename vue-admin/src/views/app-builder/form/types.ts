export type FormFieldType =
  | "input"
  | "textarea"
  | "password"
  | "number"
  | "select"
  | "radio"
  | "checkbox"
  | "switch"
  | "slider"
  | "date"
  | "daterange"
  | "time"
  | "rate"
  | "color"
  | "upload";

export interface AppBuilderOption {
  label: string;
  value: string | number;
}

export interface AppBuilderField {
  id: string;
  type: FormFieldType;
  label: string;
  field: string;
  span: number;
  required: boolean;
  placeholder?: string;
  defaultValue?: unknown;
  disabled?: boolean;
  clearable?: boolean;
  multiple?: boolean;
  options?: AppBuilderOption[];
  min?: number;
  max?: number;
  step?: number;
  rows?: number;
  dateType?: string;
  valueFormat?: string;
  accept?: string;
  action?: string;
}

export interface AppBuilderFormConfig {
  formName: string;
  labelWidth: number;
  labelPosition: "left" | "right" | "top";
  size: "large" | "default" | "small";
  gutter: number;
  submitText: string;
  resetText: string;
  showButtons: boolean;
}

export interface AppBuilderFormSchema {
  config: AppBuilderFormConfig;
  fields: AppBuilderField[];
}

export interface ComponentPreset {
  title: string;
  icon: string;
  template: Omit<AppBuilderField, "id" | "field">;
}
