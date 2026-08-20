import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;

public class TestList {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = net.minecraft.client.gui.components.AbstractSelectionList.class;
        for (Constructor<?> c : clazz.getConstructors()) {
            System.out.println("Constructor: " + c.getName());
            for (Parameter p : c.getParameters()) {
                System.out.println("  " + p.getType().getName() + " " + p.getName());
            }
        }
    }
}
