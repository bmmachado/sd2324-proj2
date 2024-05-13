package tukano.impl.api.dropbox;

public record UploadArgs(String path, boolean autorename, String mode, boolean mute, boolean strict_conflict) {
}
