package studio.webui.api;

import static io.restassured.RestAssured.when;
import static org.hamcrest.CoreMatchers.is;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import studio.webui.model.EvergreenDTOs;
import studio.webui.model.EvergreenDTOs.CommitDto;
import studio.webui.model.EvergreenDTOs.CommitDto.Commit;
import studio.webui.model.EvergreenDTOs.CommitDto.Committer;
import studio.webui.model.EvergreenDTOs.GithubClient;
import studio.webui.model.EvergreenDTOs.GithubRawClient;
import studio.webui.model.EvergreenDTOs.LatestVersionDTO;

@QuarkusTest
@TestHTTPEndpoint(EvergreenController.class)
class EvergreenControllerTest {

    @ConfigProperty(name = "version")
    String version;

    @ConfigProperty(name = "timestamp")
    String timestamp;

    @InjectMock
    @RestClient
    GithubClient githubClient;

    @InjectMock
    @RestClient
    GithubRawClient githubRawClient;

    @Test
    void testInfos() {
        when().get("infos") //
                .then().statusCode(200) //
                .body("version", is(version)) //
                .body("timestamp", is(timestamp));
    }

    @Test
    void testLatest() {
        LatestVersionDTO v = new LatestVersionDTO("0.3.1", "2021-08-15T13:04:41Z",
                "https://github.com/marian-m12l/studio/releases/tag/0.3.1");
        Mockito.when(githubClient.latestRelease()).thenReturn(CompletableFuture.completedStage(v));

        when().get("latest") //
                .then().statusCode(200) //
                .body("name", is(v.getName())) //
                .body("published_at", is(v.getPublishedAt())) //
                .body("html_url", is(v.getHtmlUrl()));
    }

    @Test
    void testAnnounceDefault() {
        // no commit -> default
        Mockito.when(githubClient.commits("ANNOUNCE.md")) //
                .thenReturn(CompletableFuture.completedStage(Arrays.asList()));

        when().get("announce") //
                .then().statusCode(200) //
                .body("date", is(EvergreenDTOs.DEFAULT_ANNOUNCE_DATE)) //
                .body("content", is(EvergreenDTOs.DEFAULT_ANNOUNCE_CONTENT));
    }

    @Test
    void testAnnounceFirst() {
        String date = "2000-01-01T00:00:00.000Z";
        String content = "Hello world!";

        // 1 commit -> latest
        Commit cc = new Commit();
        cc.setCommitter(new Committer("hello", date));
        CommitDto commitDto = new CommitDto();
        commitDto.setCommit(cc);
        Mockito.when(githubClient.commits("ANNOUNCE.md")) //
                .thenReturn(CompletableFuture.completedStage(Arrays.asList(commitDto)));
        Mockito.when(githubRawClient.announce()) //
                .thenReturn(CompletableFuture.completedStage(content));

        when().get("announce") //
                .then().statusCode(200) //
                .body("date", is(date)) //
                .body("content", is(content));

    }

}
