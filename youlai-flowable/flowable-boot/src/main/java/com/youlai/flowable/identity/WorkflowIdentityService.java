package com.youlai.flowable.identity;

import com.youlai.common.security.util.SecurityUtils;
import com.youlai.system.api.UserFeignClient;
import com.youlai.system.dto.UserAuthInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkflowIdentityService {

    private final UserFeignClient userFeignClient;

    public Long getCurrentUserId() {
        return SecurityUtils.getUserId();
    }

    public String getCurrentUsername() {
        return SecurityUtils.getUsername();
    }

    public Set<String> getCurrentRoles() {
        return SecurityUtils.getRoles();
    }

    public UserAuthInfo getUserAuthInfo(String username) {
        return userFeignClient.getUserAuthInfo(username);
    }

    public Set<String> getUserRoles(String username) {
        UserAuthInfo userAuthInfo = getUserAuthInfo(username);
        return userAuthInfo == null || userAuthInfo.getRoles() == null
                ? Collections.emptySet()
                : userAuthInfo.getRoles();
    }
}
