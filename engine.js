/**
 * ShockTrade Server/Engine Bootstrap
 * @author: lawrence.daniels@gmail.com
 */
(function () {
    require("./app-tqm/target/scala-2.11/shocktrade-server-fastopt.js");
    const facade = com.shocktrade.server.ShocktradeServerJsApp();
    facade.startServer({
        "__dirname": __dirname,
        "__filename": __filename,
        "exports": exports,
        "module": module,
        "require": require
    });
})();
