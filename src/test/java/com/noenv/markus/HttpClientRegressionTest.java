package com.noenv.markus;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import io.reactivex.rxjava3.core.Completable;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.PfxOptions;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.rxjava3.CompletableHelper;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import org.junit.*;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

import static com.github.tomakehurst.wiremock.client.WireMock.*;


@RunWith(VertxUnitRunner.class)
public class HttpClientRegressionTest {

  private static final Logger logger = LoggerFactory.getLogger(HttpClientRegressionTest.class);
  private static final WireMockServer wiremock = new WireMockServer(new WireMockConfiguration()
    .dynamicPort().dynamicHttpsPort()
    .keystoreType("PKCS12").keystorePath("keystore.p12")
    .keystorePassword("wibble").keyManagerPassword("wibble")
  );

  @BeforeClass
  public static void beforeClass() {
    wiremock.start();
  }

  @AfterClass
  public static void afterClass() {
    wiremock.stop();
  }

  private Vertx vertx;

  @Before
  public void before() {
    vertx = Vertx.vertx();
  }

  @After
  public void after(final TestContext context) {
    wiremock.resetAll();
    vertx.rxClose().subscribe(CompletableHelper.toObserver(context.asyncAssertSuccess()));
  }

  @Test
  public void shouldRequestAfterFault(final TestContext context) {
    // GIVEN
    final var options = new HttpClientOptions().setDefaultHost("localhost").setDefaultPort(wiremock.port());
    final var given = vertx.createHttpClient(options);
    // EXPECTATIONS
    wiremock.stubFor(
      WireMock.get("/some/path")
        .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE))
    );
    wiremock.stubFor(
      WireMock.get("/other/path")
        .willReturn(aResponse().withStatus(200))
    );
    // WHEN
    Completable.concatArray(
      given.rxRequest(HttpMethod.GET, "/some/path").flatMap(HttpClientRequest::rxSend).ignoreElement()
        .doOnComplete(() -> context.fail("expected an Exception here"))
        .onErrorComplete()
      ,
      given.rxRequest(HttpMethod.GET, "/other/path").flatMap(HttpClientRequest::rxSend).ignoreElement()
        .doOnError(context::fail)
    )
      .doOnComplete(() ->
        // VERIFY
        context.verify(nothing -> {
          wiremock.verify(1, getRequestedFor(urlEqualTo("/some/path")));
          wiremock.verify(1, getRequestedFor(urlEqualTo("/other/path")));
        })
      )
      // THEN
      .subscribe(CompletableHelper.toObserver(context.asyncAssertSuccess()));
  }

  @Test
  public void shouldRequestAfterFaultHttp2(final TestContext context) {
    // GIVEN
    final var options = new HttpClientOptions().setDefaultHost("localhost").setDefaultPort(wiremock.httpsPort())
      .setSsl(true).setUseAlpn(true).setVerifyHost(false)
      .setPfxTrustOptions(new PfxOptions().setPath("keystore.p12").setPassword("wibble"))
      .setAlpnVersions(Collections.singletonList(HttpVersion.HTTP_2)).setProtocolVersion(HttpVersion.HTTP_2);

    final var given = vertx.createHttpClient(options);
    // EXPECTATIONS
    wiremock.stubFor(
      WireMock.get("/successful/path")
        .willReturn(aResponse().withStatus(200))
    );
    wiremock.stubFor(
      WireMock.get("/fault/path")
        .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE))
    );
    wiremock.stubFor(
      WireMock.get("/dead/path")
        .willReturn(aResponse().withStatus(200))
    );
    // WHEN
    Completable.concatArray(
      given.rxRequest(HttpMethod.GET, "/successful/path").flatMap(HttpClientRequest::rxSend).ignoreElement()
      ,
      given.rxRequest(HttpMethod.GET, "/fault/path").flatMap(HttpClientRequest::rxSend).ignoreElement()
      .onErrorComplete()
      ,
      given.rxRequest(HttpMethod.GET, "/dead/path").flatMap(HttpClientRequest::rxSend).ignoreElement()
    )
      .doOnComplete(() ->
        // VERIFY
        context.verify(nothing -> {
          wiremock.verify(1, getRequestedFor(urlEqualTo("/successful/path")));
          wiremock.verify(1, getRequestedFor(urlEqualTo("/fault/path")));
          wiremock.verify(1, getRequestedFor(urlEqualTo("/dead/path")));
          context.assertTrue(wiremock.findAllUnmatchedRequests().isEmpty());
        })
      )
      // THEN
      .subscribe(CompletableHelper.toObserver(context.asyncAssertSuccess()));
  }
}
