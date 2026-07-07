package com.youlai.demo.monitor.service;

import com.youlai.demo.monitor.model.form.DemoProjectForm;
import com.youlai.demo.monitor.model.vo.DemoProjectVO;

import java.util.List;

public interface DemoProjectService {

    List<DemoProjectVO> listProjects();

    DemoProjectVO getProject(Long id);

    DemoProjectVO createProject(DemoProjectForm form);

    DemoProjectVO updateProject(Long id, DemoProjectForm form);

    void deleteProject(Long id);
}
