package shop.http.clients

import cats.implicits._
import org.http4s._
import org.http4s.circe._
import org.http4s.circe.CirceEntityDecoder
import org.http4s.client._
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.Method._
import shop.config.data.PaymentConfig
import shop.domain.order._
import shop.domain.payment._
import shop.http.json._
import shop.effects._

trait PaymentClient[F[_]] {
  def process(payment: Payment): F[PaymentId]
}

final class LivePaymentClient[F[_]: MonadThrow](
    cfg: PaymentConfig,
    client: Client[F]
) extends PaymentClient[F] with Http4sClientDsl[F] with CirceEntityDecoder {

  def process(payment: Payment): F[PaymentId] =
    Uri.fromString(cfg.uri.value.value + "/payments").liftTo[F].flatMap {
      uri =>
        client.fetch[PaymentId](POST(payment, uri)) {
          r =>
            if (r.status == Status.Ok || r.status == Status.Conflict)
              r.asJsonDecode[PaymentId]
            else
              PaymentError(
                Option(r.status.reason).getOrElse("unknown")
              ).raiseError[F, PaymentId]
        }
    }

}
