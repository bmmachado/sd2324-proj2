package tukano.impl.api.dropbox;

import java.util.List;

public class DeleteAllArgs {

    List<Entry> entries;

  public  DeleteAllArgs(List<Entry> entries) {
        this.entries = entries;
    }
    public record Entry(String path) {}

    public List<Entry> getEntries() {
        return entries;
    }

    public void addEntries(String entry) {
        this.entries.add(new Entry(entry));
    }

}
