import com.spring_ai.ai_copilot.client.AiProviderClient;
import com.spring_ai.ai_copilot.exception.AiExceptionTranslator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
public class Probe {
  public static void main(String[] args) {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    ChatModel fake = prompt -> new ChatResponse(java.util.List.of(new Generation(new AssistantMessage("ok"))));
    AiProviderClient client = new AiProviderClient(ChatClient.builder(fake), StructuredOutputValidationAdvisor.builder().outputType(com.spring_ai.ai_copilot.dto.FeedbackAnalysis.class).maxRepeatAttempts(3).build(), MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().build()).build(), CircuitBreakerRegistry.ofDefaults(), RateLimiterRegistry.ofDefaults(), registry, new AiExceptionTranslator());
    client.recordAiMetrics("chat.response","success",null,250L);
    System.out.println(registry.counter("ai.requests","provider","groq","model","openai/gpt-oss-120b","operation","chat.response","status","success").count());
  }
}
