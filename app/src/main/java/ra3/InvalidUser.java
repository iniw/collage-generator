package ra3;

public class InvalidUser extends Exception {
    public InvalidUser(String user) {
        super("Usuário inválido: " + user);
    }
}
