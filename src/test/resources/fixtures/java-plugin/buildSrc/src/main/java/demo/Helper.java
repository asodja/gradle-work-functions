package demo;

public final class Helper {

    private Helper() {
    }

    public static String versionOf(GenerateVersion.In input) {
        return input.version();
    }
}
