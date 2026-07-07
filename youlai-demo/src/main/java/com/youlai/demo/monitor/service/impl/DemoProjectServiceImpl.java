package com.youlai.demo.monitor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.youlai.demo.monitor.mapper.DemoProjectMapper;
import com.youlai.demo.monitor.model.entity.DemoProject;
import com.youlai.demo.monitor.model.form.DemoProjectForm;
import com.youlai.demo.monitor.model.vo.DemoProjectVO;
import com.youlai.demo.monitor.service.DemoProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class DemoProjectServiceImpl implements DemoProjectService {

    private final DemoProjectMapper demoProjectMapper;

    @Override
    public List<DemoProjectVO> listProjects() {
        LambdaQueryWrapper<DemoProject> queryWrapper = new LambdaQueryWrapper<DemoProject>()
                .orderByAsc(DemoProject::getId);
        return demoProjectMapper.selectList(queryWrapper).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public DemoProjectVO getProject(Long id) {
        return toVO(getProjectEntity(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DemoProjectVO createProject(DemoProjectForm form) {
        DemoProject project = new DemoProject();
        fillProject(project, form);
        demoProjectMapper.insert(project);
        return toVO(project);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DemoProjectVO updateProject(Long id, DemoProjectForm form) {
        DemoProject project = getProjectEntity(id);
        fillProject(project, form);
        demoProjectMapper.updateById(project);
        return toVO(demoProjectMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProject(Long id) {
        getProjectEntity(id);
        demoProjectMapper.deleteById(id);
    }

    private DemoProject getProjectEntity(Long id) {
        DemoProject project = demoProjectMapper.selectById(id);
        if (project == null) {
            throw new NoSuchElementException("Demo 项目不存在：" + id);
        }
        return project;
    }

    private void fillProject(DemoProject project, DemoProjectForm form) {
        project.setName(form.getName());
        project.setOwner(form.getOwner());
        project.setStatus(form.getStatus() == null ? "TODO" : form.getStatus());
        project.setDescription(form.getDescription());
    }

    private DemoProjectVO toVO(DemoProject project) {
        return DemoProjectVO.builder()
                .id(project.getId())
                .name(project.getName())
                .owner(project.getOwner())
                .status(project.getStatus())
                .description(project.getDescription())
                .createdAt(project.getCreateTime())
                .updatedAt(project.getUpdateTime())
                .build();
    }
}
