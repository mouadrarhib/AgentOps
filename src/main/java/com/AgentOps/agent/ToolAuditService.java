package com.AgentOps.agent;

import com.AgentOps.entities.ToolAudit;
import com.AgentOps.entities.enums.ToolResultStatus;
import com.AgentOps.helpers.UserHelper;
import com.AgentOps.repositories.ToolAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ToolAuditService {
    private final ToolAuditRepository audits;
    private final UserHelper users;

    public ToolAuditService(ToolAuditRepository audits, UserHelper users) {
        this.audits = audits;
        this.users = users;
    }

    @Transactional
    public void record(Long userId, String name, String summary, ToolResultStatus status) {
        ToolAudit audit = new ToolAudit();
        audit.setUser(users.getUserById(userId));
        audit.setToolName(name);
        audit.setArgumentsSummary(summary);
        audit.setResultStatus(status);
        audits.saveAndFlush(audit);
    }
}
