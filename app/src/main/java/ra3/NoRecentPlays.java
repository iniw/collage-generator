package ra3;

public class NoRecentPlays extends Exception {
    NoRecentPlays(String user) {
        super("Usuário sem músicas recentes: " + user);
    }
}
