import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.inject.Inject;
import org.jboss.logging.Logger;
import java.io.*;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.Path;
import javax.ws.rs.POST;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

@QuarkusMain
public class Application {
    public static void main(String... args) {
        Quarkus.run(args);
    }
}

@Path("/api")
public class OrchestrationResource {
    @Inject
    SpeechService speechService;

    @Inject
    AgentService agentService;

    private static final Logger LOGGER = Logger.getLogger(OrchestrationResource.class);

    @POST
    @Path("/convert")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response convertAudio(InputStream inputStream) {
        try {
            // Save audio input to file
            File audioFile = File.createTempFile("audio_question", ".wav");
            try (FileOutputStream out = new FileOutputStream(audioFile)) {
                inputStream.transferTo(out);
            }

            // Call Speech-to-Text API
            String questionText = speechService.convertAudioToText(audioFile);

            // Clean text
            String cleanQuestionText = questionText.replaceAll("[^a-zA-Z0-9áéíóúãõçÁÉÍÓÚÃÕÇ\\s]", "");

            // Call Agent API with question
            String agentResponse = agentService.getAgentResponse(cleanQuestionText);

            // Generate TTS audio
            File responseAudioFile = speechService.textToSpeech(agentResponse);

            // Return generated audio
            return Response.ok(responseAudioFile).build();
        } catch (Exception e) {
            LOGGER.error("Error processing request", e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Error processing request").build();
        }
    }

    @RegisterRestClient(configKey="speech-api")
    public interface SpeechService {
        @POST
        @Path("/speech/recognition/conversation/cognitiveservices/v1")
        @Consumes("audio/wav")
        @Produces(MediaType.APPLICATION_JSON)
        String convertAudioToText(File audioFile);

        @POST
        @Path("/cognitiveservices/v1")
        @Consumes("application/ssml+xml")
        @Produces("audio/wav")
        File textToSpeech(String text);
    }

    @RegisterRestClient(configKey="agent-api")
    public interface AgentService {
        @POST
        @Path("/acs/llms/agent")
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        String getAgentResponse(String questionText);
    }
}