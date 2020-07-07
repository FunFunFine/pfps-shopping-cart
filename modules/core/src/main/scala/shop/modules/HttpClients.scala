package shop.modules

import cats.effect._
import cats.implicits._
import org.http4s.client.Client
import shop.config.data.PaymentConfig
import shop.effects.MonadThrow
import shop.http.clients._
import tofu.HasContext

object HttpClients {

  def make[F[_]: MonadThrow](
      )(implicit
        hasCfg: F HasContext PaymentConfig,
        hasClient: F HasContext Client[F]): F[HttpClients[F]] =
    (hasCfg.context, hasClient.context).mapN {
      case (cfg, client) =>
        new HttpClients[F] {
          def payment: PaymentClient[F] = new LivePaymentClient[F](cfg, client)
        }
    }
}

trait HttpClients[F[_]] {
  def payment: PaymentClient[F]
}
