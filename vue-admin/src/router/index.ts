import { createRouter, createWebHashHistory, RouteRecordRaw } from "vue-router";

export const Layout = () => import("@/layout/index.vue");

// 静态路由
export const constantRoutes: RouteRecordRaw[] = [
  {
    path: "/redirect",
    component: Layout,
    meta: { hidden: true },
    children: [
      {
        path: "/redirect/:path(.*)",
        component: () => import("@/views/redirect/index.vue"),
      },
    ],
  },

  {
    path: "/login",
    component: () => import("@/views/login/index.vue"),
    meta: { hidden: true },
  },

  {
    path: "/",
    name: "/",
    component: Layout,
    redirect: "/dashboard",
    meta: {
      title: "dashboard",
      icon: "homepage",
    },
    children: [
      {
        path: "app-builder/apps",
        component: () => import("@/views/app-builder/apps/index.vue"),
        name: "AppBuilderApps",
        meta: {
          title: "应用中心",
          icon: "project",
          keepAlive: true,
        },
      },
      {
        path: "app-builder/models",
        component: () => import("@/views/app-builder/models/index.vue"),
        name: "AppBuilderModels",
        meta: {
          title: "数据模型",
          icon: "tree",
          keepAlive: true,
        },
      },
      {
        path: "app-builder/form",
        component: () => import("@/views/app-builder/form/index.vue"),
        name: "AppBuilderFormDesigner",
        meta: {
          title: "表单设计器",
          icon: "cascader",
          keepAlive: true,
        },
      },
      {
        path: "app-builder/pages",
        component: () => import("@/views/app-builder/pages/index.vue"),
        name: "AppBuilderPages",
        meta: {
          title: "页面配置",
          icon: "document",
          keepAlive: true,
        },
      },
      {
        path: "app-builder/data",
        component: () => import("@/views/app-builder/data/index.vue"),
        name: "AppBuilderData",
        meta: {
          title: "业务数据",
          icon: "dict_item",
          keepAlive: true,
        },
      },
      {
        path: "app-builder/extensions",
        component: () => import("@/views/app-builder/extensions/index.vue"),
        name: "AppBuilderExtensions",
        meta: {
          title: "扩展中心",
          icon: "api",
          keepAlive: true,
        },
      },
      {
        path: "app-builder/runtime/:pageId",
        component: () => import("@/views/app-builder/runtime/index.vue"),
        name: "AppBuilderRuntime",
        meta: {
          title: "业务页面",
          icon: "document",
          hidden: true,
        },
      },
      {
        path: "workflow/center",
        component: () => import("@/views/workflow/center/index.vue"),
        name: "WorkflowCenter",
        meta: {
          title: "流程中心",
          icon: "todolist",
          keepAlive: true,
        },
      },
      {
        path: "workflow/designer",
        component: () => import("@/views/workflow/designer/index.vue"),
        name: "WorkflowDesigner",
        meta: {
          title: "流程设计器",
          icon: "cascader",
          keepAlive: false,
        },
      },
      {
        path: "workflow/tasks",
        component: () => import("@/views/workflow/tasks/index.vue"),
        name: "WorkflowTasks",
        meta: {
          title: "审批中心",
          icon: "todolist",
          keepAlive: true,
        },
      },
      {
        path: "workflow/detail/:processInstanceId",
        component: () => import("@/views/workflow/detail/index.vue"),
        name: "WorkflowDetail",
        meta: {
          title: "审批详情",
          icon: "document",
          hidden: true,
        },
      },
      {
        path: "collect",
        redirect: "/collect/models",
        name: "Collect",
        meta: {
          title: "数据采集",
          icon: "api",
          alwaysShow: true,
        },
        children: [
          {
            path: "models",
            component: () => import("@/views/collect/console/index.vue"),
            name: "CollectModels",
            meta: {
              title: "采集模型",
              icon: "tree",
              keepAlive: true,
            },
          },
          {
            path: "apis",
            component: () => import("@/views/collect/console/index.vue"),
            name: "CollectApis",
            meta: {
              title: "采集接口",
              icon: "api",
              keepAlive: true,
            },
          },
          {
            path: "db-sources",
            component: () => import("@/views/collect/console/index.vue"),
            name: "CollectDbSources",
            meta: {
              title: "DB 数据源",
              icon: "redis",
              keepAlive: true,
            },
          },
          {
            path: "tasks",
            component: () => import("@/views/collect/console/index.vue"),
            name: "CollectTasks",
            meta: {
              title: "采集任务",
              icon: "todolist",
              keepAlive: true,
            },
          },
          {
            path: "instances",
            component: () => import("@/views/collect/console/index.vue"),
            name: "CollectInstances",
            meta: {
              title: "执行实例",
              icon: "monitor",
              keepAlive: true,
            },
          },
        ],
      },
      {
        path: "401",
        component: () => import("@/views/error-page/401.vue"),
        meta: { hidden: true },
      },
      {
        path: "404",
        component: () => import("@/views/error-page/404.vue"),
        meta: { hidden: true },
      },
      {
        path: "/OAuth2Redirect",
        name: "OAuth2Redirect",
        component: () => import("@/views/login/OAuth2Redirect.vue"),
        meta: { hidden: true },
      },
    ],
  },
];

/**
 * 创建路由
 */
const router = createRouter({
  history: createWebHashHistory(),
  routes: constantRoutes as RouteRecordRaw[],
  // 刷新时，滚动条位置还原
  scrollBehavior: () => ({ left: 0, top: 0 }),
});

/**
 * 重置路由
 */
export function resetRouter() {
  router.replace({ path: "/login" });
}

export default router;
