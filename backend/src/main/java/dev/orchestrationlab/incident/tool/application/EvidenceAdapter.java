package dev.orchestrationlab.incident.tool.application;

import java.util.List;

public interface EvidenceAdapter {
    ToolKind kind();
    List<String> read(ToolInput input) throws Exception;
}
