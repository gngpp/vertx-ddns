package com.zf1976.ddns.verticle.provider;

import com.zf1976.ddns.util.ObjectUtil;
import com.zf1976.ddns.util.RsaUtil;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authentication.AuthenticationProvider;
import io.vertx.ext.auth.impl.UserImpl;

import java.util.Map;

/**
 * @author ant
 * Create by Ant on 2021/8/4 1:13 AM
 */
public record UsernamePasswordAuthenticationProvider(
        SecureProvider secureProvider) implements AuthenticationProvider {

    private static final String usernameKey = "username";
    private static final String passwordKey = "password";

    /**
     * Authenticate a user.
     * <p>
     * The first argument is a JSON object containing information for authenticating the user. What this actually contains
     * depends on the specific implementation. In the case of a simple username/password based
     * authentication it is likely to contain a JSON object with the following structure:
     * <pre>
     *   {
     *     "username": "tim",
     *     "password": "mypassword"
     *   }
     * </pre>
     * For other types of authentication it contain different information - for example a JWT token or OAuth bearer token.
     * <p>
     * If the user is successfully authenticated a {@link User} object is passed to the handler in an {@link AsyncResult}.
     * The user object can then be used for authorisation.
     *
     * @param credentials   The credentials
     * @param resultHandler The result handler
     */
    @Override
    public void authenticate(JsonObject credentials, Handler<AsyncResult<User>> resultHandler) {
        final var user = new UserImpl(credentials);
        this.secureProvider.readRsaKeyPair()
                           .compose(rsaKeyPair -> this.secureProvider.readLoginConfig()
                                                                     .compose(loginConfig -> this.checkAuthentication(loginConfig, user, rsaKeyPair)))
                           .onComplete(event -> {
                               if (event.succeeded()) {
                                   resultHandler.handle(Future.succeededFuture(user));
                               } else {
                                   resultHandler.handle(Future.failedFuture(event.cause()));
                               }
                           });
    }

    private Future<User> checkAuthentication(Map<String, String> usernamePassword, User user, RsaUtil.RsaKeyPair rsaKeyPair) {

        try {
            final var decodeUsername = RsaUtil.decryptByPrivateKey(rsaKeyPair.getPrivateKey(), user.get(usernameKey));
            final var decodePassword = RsaUtil.decryptByPrivateKey(rsaKeyPair.getPrivateKey(), user.get(passwordKey));
            final var jsonObject = new JsonObject()
                    .put(usernameKey, decodeUsername.trim())
                    .put(passwordKey, decodePassword.trim());
            return this.checkUser(usernamePassword, new UserImpl(jsonObject));
        } catch (Exception e) {
            return Future.failedFuture("server decryption verification error!");
        }
    }

    private Future<User> checkUser(Map<String, String> usernamePassword, User user) {
        final var username = (String) user.get(usernameKey);
        final var password = (String) user.get(passwordKey);
        if (ObjectUtil.nullSafeEquals(usernamePassword.get(usernameKey), username)
                && ObjectUtil.nullSafeEquals(usernamePassword.get(passwordKey), password)) {
            return Future.succeededFuture(user);
        }
        return Future.failedFuture("wrong user name or password!");
    }

}