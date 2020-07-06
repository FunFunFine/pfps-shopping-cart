package shop.config

import ciris._
import eu.timepit.refined.types.net.UserPortNumber
import eu.timepit.refined.types.numeric.PosInt
import eu.timepit.refined.types.string.NonEmptyString
import io.estatico.newtype.macros.newtype
import scala.concurrent.duration._
import tofu.optics.macros._
object data {

  @newtype case class AdminUserTokenConfig(value: Secret[NonEmptyString])
  @newtype case class JwtSecretKeyConfig(value: Secret[NonEmptyString])
  @newtype case class JwtClaimConfig(value: Secret[NonEmptyString])
  @newtype case class TokenExpiration(value: FiniteDuration)

  @newtype case class PasswordSalt(value: Secret[NonEmptyString])

  @newtype case class ShoppingCartExpiration(value: FiniteDuration)

  @ClassyOptics
  case class CheckoutConfig(
      retriesLimit: PosInt,
      retriesBackoff: FiniteDuration
  )

  @ClassyOptics
  case class AppConfig(
  @promote adminJwtConfig: AdminJwtConfig,
      tokenConfig: JwtSecretKeyConfig,
      passwordSalt: PasswordSalt,
      tokenExpiration: TokenExpiration,
      cartExpiration: ShoppingCartExpiration,
      @promote checkoutConfig: CheckoutConfig,
      paymentConfig: PaymentConfig,
      @promote httpClientConfig: HttpClientConfig,
      @promote postgreSQL: PostgreSQLConfig,
      redis: RedisConfig,
      @promote httpServerConfig: HttpServerConfig
  )
  @ClassyOptics
  case class AdminJwtConfig(
      secretKey: JwtSecretKeyConfig,
      claimStr: JwtClaimConfig,
      adminToken: AdminUserTokenConfig
  )
  @ClassyOptics
  case class PostgreSQLConfig(
      host: NonEmptyString,
      port: UserPortNumber,
      user: NonEmptyString,
      database: NonEmptyString,
      max: PosInt
  )

  @newtype case class RedisURI(value: NonEmptyString)
  @newtype case class RedisConfig(uri: RedisURI)

  @newtype case class PaymentURI(value: NonEmptyString)
  @newtype case class PaymentConfig(uri: PaymentURI)
  @ClassyOptics
  case class HttpServerConfig(
      host: NonEmptyString,
      port: UserPortNumber
  )
  @ClassyOptics
  case class HttpClientConfig(
      connectTimeout: FiniteDuration,
      requestTimeout: FiniteDuration
  )

}
