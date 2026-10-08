package dev.orchestrationlab.documentation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.support.ToolCallbacks;

@SpringBootApplication
public class DocumentationServerApplication {
    public static void main(String[] args) { SpringApplication.run(DocumentationServerApplication.class, args); }
    @Bean ToolCallbackProvider documentationTools() { return ToolCallbackProvider.from(ToolCallbacks.from(new RunbookTools())); }
}
