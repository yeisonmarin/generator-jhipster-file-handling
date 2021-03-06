package <%= packageName %>.internal.messaging;

import <%= packageName %>.<%= appName %>App;
import <%= packageName %>.internal.messaging.fileNotification.FileNotification;
import <%= packageName %>.internal.messaging.fileNotification.FileNotificationStreams;
import <%= packageName %>.internal.messaging.platform.MessageService;
import <%= packageName %>.internal.messaging.platform.TokenizableMessage;
import <%= packageName %>.internal.util.TokenGenerator;
import <%= packageName %>.service.<%= classNamesPrefix %>MessageTokenService;
import <%= packageName %>.service.dto.<%= classNamesPrefix %>MessageTokenDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.test.binder.MessageCollector;
import org.springframework.data.domain.Pageable;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {<%= appName %>App.class})
public class FileNotificationControllerIT {

    private final static Logger log = LoggerFactory.getLogger(FileNotificationControllerIT.class);

    @Autowired
    private MessageService<TokenizableMessage<String>, <%= classNamesPrefix %>MessageTokenDTO> fileNotificationMessageService;

    @Autowired
    private MessageCollector messageCollector;

    @Autowired
    private FileNotificationStreams fileNotificationStreams;

    @Autowired
    private TokenGenerator tokenGenerator;

    @Autowired
    private <%= classNamesPrefix %>MessageTokenService messageTokenService;

    @AfterEach
    void tearDown() {
        log.debug("Right, time for some manual message-token-table cleanup");
        messageTokenService.findAll(Pageable.unpaged())
                           .stream()
                           .map(<%= classNamesPrefix %>MessageTokenDTO::getId)
                           .forEach(messageTokenService::delete);
    }


    @Test
    public void callNotificationService() throws Exception {

        long timestamp = System.currentTimeMillis();
        String fileId = "1001";
        String filename = "AssetAdditions2019.xlsx";
        String description = "Assets Acquired in FY 2019";

        final FileNotification fileNotification = FileNotification.builder().description(description).fileId(fileId).filename(filename).timestamp(timestamp).build();

        final FileNotification unMutatedFileNotification = SerializationUtils.clone(fileNotification);

        <%= classNamesPrefix %>MessageTokenDTO messageToken = fileNotificationMessageService.sendMessage(fileNotification);

        BlockingQueue<?> mq = messageCollector.forChannel(fileNotificationStreams.outbound());
        assertThat(mq).isNotNull();

        // TODO FIX NULL POINTER EXCEPTION AND TEST WITH JAVA 13
        Object payload =
            Objects.requireNonNull(
                messageCollector.forChannel(fileNotificationStreams.outbound()).poll(10000, TimeUnit.MILLISECONDS).getPayload());

        // Check that message-token has been created in the db
        assertThat(messageToken.getId()).isNotNull();
        assertThat(messageToken.getTokenValue()).isEqualTo(tokenGenerator.md5Digest(unMutatedFileNotification));
        assertThat(messageToken.getTimeSent()).isEqualTo(fileNotification.getTimestamp());
        //        assertThat(payload.toString()).containsSequence(String.valueOf(timestamp));
        assertThat(payload.toString()).containsSequence(description);
        assertThat(payload.toString()).containsSequence(messageToken.getTokenValue());
    }
}
