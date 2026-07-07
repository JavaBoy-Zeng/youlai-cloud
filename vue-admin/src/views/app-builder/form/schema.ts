import type {
  ComponentPreset,
  AppBuilderField,
  AppBuilderFormConfig,
} from "./types";
import { cloneSerializable } from "./clone";

export const defaultFormConfig: AppBuilderFormConfig = {
  formName: "业务表单",
  labelWidth: 100,
  labelPosition: "right",
  size: "default",
  gutter: 16,
  submitText: "提交",
  resetText: "重置",
  showButtons: true,
};

export const componentGroups: Array<{
  title: string;
  list: ComponentPreset[];
}> = [
  {
    title: "输入型组件",
    list: [
      {
        title: "单行文本",
        icon: "Edit",
        template: {
          type: "input",
          label: "单行文本",
          span: 24,
          required: true,
          placeholder: "请输入",
          defaultValue: "",
          clearable: true,
        },
      },
      {
        title: "多行文本",
        icon: "Document",
        template: {
          type: "textarea",
          label: "多行文本",
          span: 24,
          required: false,
          placeholder: "请输入",
          defaultValue: "",
          rows: 4,
        },
      },
      {
        title: "密码",
        icon: "Lock",
        template: {
          type: "password",
          label: "密码",
          span: 24,
          required: true,
          placeholder: "请输入",
          defaultValue: "",
          clearable: true,
        },
      },
      {
        title: "数字",
        icon: "Odometer",
        template: {
          type: "number",
          label: "数字",
          span: 24,
          required: false,
          defaultValue: 0,
          min: 0,
          step: 1,
        },
      },
    ],
  },
  {
    title: "选择型组件",
    list: [
      {
        title: "下拉选择",
        icon: "ArrowDown",
        template: {
          type: "select",
          label: "下拉选择",
          span: 24,
          required: true,
          placeholder: "请选择",
          defaultValue: "",
          clearable: true,
          options: [
            { label: "选项一", value: "1" },
            { label: "选项二", value: "2" },
          ],
        },
      },
      {
        title: "单选框组",
        icon: "CircleCheck",
        template: {
          type: "radio",
          label: "单选框组",
          span: 24,
          required: true,
          defaultValue: "1",
          options: [
            { label: "选项一", value: "1" },
            { label: "选项二", value: "2" },
          ],
        },
      },
      {
        title: "多选框组",
        icon: "Select",
        template: {
          type: "checkbox",
          label: "多选框组",
          span: 24,
          required: false,
          defaultValue: [],
          options: [
            { label: "选项一", value: "1" },
            { label: "选项二", value: "2" },
          ],
        },
      },
      {
        title: "开关",
        icon: "Open",
        template: {
          type: "switch",
          label: "开关",
          span: 24,
          required: false,
          defaultValue: false,
        },
      },
      {
        title: "滑块",
        icon: "Operation",
        template: {
          type: "slider",
          label: "滑块",
          span: 24,
          required: false,
          defaultValue: 0,
          min: 0,
          max: 100,
          step: 1,
        },
      },
    ],
  },
  {
    title: "高级组件",
    list: [
      {
        title: "日期选择",
        icon: "Calendar",
        template: {
          type: "date",
          label: "日期选择",
          span: 24,
          required: true,
          placeholder: "请选择",
          defaultValue: "",
          dateType: "date",
          valueFormat: "YYYY-MM-DD",
        },
      },
      {
        title: "日期范围",
        icon: "Calendar",
        template: {
          type: "daterange",
          label: "日期范围",
          span: 24,
          required: false,
          defaultValue: [],
          dateType: "daterange",
          valueFormat: "YYYY-MM-DD",
        },
      },
      {
        title: "时间选择",
        icon: "Clock",
        template: {
          type: "time",
          label: "时间选择",
          span: 24,
          required: false,
          placeholder: "请选择",
          defaultValue: "",
          valueFormat: "HH:mm:ss",
        },
      },
      {
        title: "评分",
        icon: "Star",
        template: {
          type: "rate",
          label: "评分",
          span: 24,
          required: false,
          defaultValue: 0,
          max: 5,
        },
      },
      {
        title: "颜色选择",
        icon: "Brush",
        template: {
          type: "color",
          label: "颜色选择",
          span: 24,
          required: false,
          defaultValue: "",
        },
      },
      {
        title: "上传",
        icon: "Upload",
        template: {
          type: "upload",
          label: "上传",
          span: 24,
          required: false,
          defaultValue: [],
          action: "/youlai-system/api/v1/files",
          accept: "",
        },
      },
    ],
  },
];

export function createField(
  template: ComponentPreset["template"],
  index: number
): AppBuilderField {
  const suffix = Date.now().toString(36) + index.toString(36);
  return {
    id: `field_${suffix}`,
    field: `${template.type}_${index}`,
    disabled: false,
    ...cloneSerializable(template),
  };
}
