package shop.modules

import cats.Parallel
import cats.effect._
import cats.implicits._
import dev.profunktor.redis4cats.RedisCommands
import shop.algebras._
import shop.config.data._
import skunk._
import tofu.HasContext

object Algebras {

  def make[F[_]: Concurrent: Parallel: Timer](
      implicit hasRedis: F HasContext RedisCommands[F, String, String],
      hasSessionPool: F HasContext Resource[F, Session[F]],
      hasCartExpiration: F HasContext ShoppingCartExpiration
  ): F[Algebras[F]] =
    for {
      sessionPool    <- hasSessionPool.context
      redis          <- hasRedis.context
      cartExpiration <- hasCartExpiration.context
      brands         <- LiveBrands.make[F](sessionPool)
      categories     <- LiveCategories.make[F](sessionPool)
      items          <- LiveItems.make[F](sessionPool)
      cart           <- LiveShoppingCart.make[F](items, redis, cartExpiration)
      orders         <- LiveOrders.make[F](sessionPool)
      health         <- LiveHealthCheck.make[F](sessionPool, redis)
    } yield new Algebras[F](cart, brands, categories, items, orders, health)

  final class Algebras[F[_]](
      val cart: ShoppingCart[F],
      val brands: Brands[F],
      val categories: Categories[F],
      val items: Items[F],
      val orders: Orders[F],
      val healthCheck: HealthCheck[F]
  )

}
