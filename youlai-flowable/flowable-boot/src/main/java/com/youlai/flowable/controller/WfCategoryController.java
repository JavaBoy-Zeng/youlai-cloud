package com.youlai.flowable.controller;

import com.youlai.common.result.Result;
import com.youlai.flowable.model.form.WfCategoryForm;
import com.youlai.flowable.model.vo.WfCategoryVO;
import com.youlai.flowable.service.WfCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "流程分类接口")
@RestController
@RequiredArgsConstructor
@PreAuthorize("@ss.hasPerm('workflow:*')")
@RequestMapping("/api/v1/workflow/categories")
public class WfCategoryController {

    private final WfCategoryService categoryService;

    @Operation(summary = "流程分类列表")
    @GetMapping
    public Result<List<WfCategoryVO>> listCategories() {
        return Result.success(categoryService.listCategories());
    }

    @Operation(summary = "保存流程分类")
    @PostMapping
    public Result saveCategory(@Valid @RequestBody WfCategoryForm form) {
        return Result.judge(categoryService.saveCategory(form));
    }

    @Operation(summary = "修改流程分类")
    @PutMapping("/{id}")
    public Result updateCategory(@PathVariable Long id, @Valid @RequestBody WfCategoryForm form) {
        form.setId(id);
        return Result.judge(categoryService.saveCategory(form));
    }

    @Operation(summary = "删除流程分类")
    @DeleteMapping("/{id}")
    public Result deleteCategory(@PathVariable Long id) {
        return Result.judge(categoryService.removeById(id));
    }
}
