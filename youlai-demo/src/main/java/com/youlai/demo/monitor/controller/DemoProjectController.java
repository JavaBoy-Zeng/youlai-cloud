package com.youlai.demo.monitor.controller;

import com.youlai.common.result.Result;
import com.youlai.demo.monitor.model.form.DemoProjectForm;
import com.youlai.demo.monitor.model.vo.DemoProjectVO;
import com.youlai.demo.monitor.service.DemoProjectService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 项目 Demo 控制器。
 */
@RestController
@RequestMapping("/api/v1/demo/projects")
@RequiredArgsConstructor
public class DemoProjectController {

    private final DemoProjectService demoProjectService;

    @GetMapping
    public Result<List<DemoProjectVO>> listProjects() {
        return Result.success(demoProjectService.listProjects());
    }

    @GetMapping("/{id}")
    public Result<DemoProjectVO> getProject(
            @Parameter(description = "项目ID") @PathVariable Long id
    ) {
        return Result.success(demoProjectService.getProject(id));
    }

    @PostMapping
    public Result<DemoProjectVO> createProject(@Valid @RequestBody DemoProjectForm form) {
        return Result.success(demoProjectService.createProject(form));
    }

    @PutMapping("/{id}")
    public Result<DemoProjectVO> updateProject(
            @Parameter(description = "项目ID") @PathVariable Long id,
            @Valid @RequestBody DemoProjectForm form
    ) {
        return Result.success(demoProjectService.updateProject(id, form));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteProject(
            @Parameter(description = "项目ID") @PathVariable Long id
    ) {
        demoProjectService.deleteProject(id);
        return Result.success();
    }
}
