package com.youlai.demo.service.impl;

import com.youlai.demo.monitor.mapper.DemoProjectMapper;
import com.youlai.demo.monitor.model.entity.DemoProject;
import com.youlai.demo.monitor.model.form.DemoProjectForm;
import com.youlai.demo.monitor.service.impl.DemoProjectServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoProjectServiceImplTest {

    @Mock
    private DemoProjectMapper demoProjectMapper;

    @InjectMocks
    private DemoProjectServiceImpl demoProjectService;

    @Test
    void shouldCreateProject() {
        DemoProjectForm form = new DemoProjectForm();
        form.setName("接口联调 Demo");
        form.setOwner("tester");
        form.setStatus("TODO");
        form.setDescription("用于验证新增流程。");

        demoProjectService.createProject(form);

        verify(demoProjectMapper).insert(any(DemoProject.class));
    }

    @Test
    void shouldQueryProject() {
        DemoProject project = new DemoProject();
        project.setId(1L);
        project.setName("数据库 Demo");
        project.setOwner("admin");
        project.setStatus("DONE");
        when(demoProjectMapper.selectById(1L)).thenReturn(project);

        assertThat(demoProjectService.getProject(1L).getName()).isEqualTo("数据库 Demo");
    }

    @Test
    void shouldThrowWhenProjectNotFound() {
        when(demoProjectMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> demoProjectService.getProject(999L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Demo 项目不存在");
    }
}
