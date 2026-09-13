import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class test {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        // 把你的明文密码替换进去，例如 123456
        String encode = encoder.encode("123456");
        System.out.println(encode);
    }
}
