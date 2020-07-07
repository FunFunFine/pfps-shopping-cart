package shop.modules

import cats.effect._
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import retry.RetryPolicies._
import retry.RetryPolicy
import shop.config.data.CheckoutConfig
import shop.effects._
import shop.modules.Algebras.Algebras
import shop.programs._
import tofu.HasContext

object Programs {

  def make[F[_]: Background: Logger: MonadThrow: Timer](
      algebras: Algebras[F],
      clients: HttpClients[F]
  )(implicit checkoutConfig: F HasContext CheckoutConfig): F[Programs[F]] =
    checkoutConfig.context.map(new Programs[F](_, algebras, clients))

}

final class Programs[F[_]: Background: Logger: MonadThrow: Timer] private (
    cfg: CheckoutConfig,
    algebras: Algebras[F],
    clients: HttpClients[F]
) {

  val retryPolicy: RetryPolicy[F] =
    limitRetries[F](cfg.retriesLimit.value) |+| exponentialBackoff[F](cfg.retriesBackoff)

  val checkout: CheckoutProgram[F] = new CheckoutProgram[F](
    clients.payment,
    algebras.cart,
    algebras.orders,
    retryPolicy
  )

}
