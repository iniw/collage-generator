package ra3;

public class NoImagesAvailable extends Exception {
    NoImagesAvailable(String user) {
        super("Usuário sem imagens disponíveis: " + user);
    }
}
