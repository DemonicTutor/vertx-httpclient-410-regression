Reproducer to show an issue in HttpClient - dead after failed request

Exception:
  `io.netty.handler.codec.http2.Http2Exception: Attempted to create stream id 5 after connection was closed`

keystore generated via:
  `keytool -genkeypair -alias test -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore keystore.p12 -validity 3650`
