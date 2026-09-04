package dev.kernel.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Minimal launcher entry point that delegates to Fabric Loader.
 */
public final class KernelKnotClient {
    private static final String[] FABRIC_CLIENTS = {
        "net.fabricmc.loader.impl.launch.knot.KnotClient",
        "net.fabricmc.loader.launch.knot.KnotClient"
    };

    private KernelKnotClient() {
    }

    public static void main(String[] arguments) throws Throwable {
        Class<?> fabricClient = findFabricClient();
        Method main = fabricClient.getMethod("main", String[].class);

        try {
            main.invoke(null, (Object) arguments);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private static Class<?> findFabricClient() throws ClassNotFoundException {
        for (String className : FABRIC_CLIENTS) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException ignored) {
                // Try the next known Fabric Loader package.
            }
        }

        throw new ClassNotFoundException(
            "Kernel Knot Client could not find Fabric Loader's KnotClient on the launcher classpath."
        );
    }
}
