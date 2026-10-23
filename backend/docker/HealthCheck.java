import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

// 実行イメージ（DHI）にはシェルも curl も無いため、healthcheck は JRE だけで actuator を叩く。
// 接続失敗などの例外は未捕捉のまま終了コード 1 になり、unhealthy として扱われる。
public class HealthCheck {
  public static void main(String[] args) throws Exception {
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:8080/actuator/health"))
            .timeout(Duration.ofSeconds(2))
            .build();
    int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    System.exit(status == 200 ? 0 : 1);
  }
}
