package terminus.co.edu.ufps.identidad_validacion;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import terminus.co.edu.ufps.identidad_validacion.ms1.security.AppwriteSessionVerifier;
import terminus.co.edu.ufps.identidad_validacion.ms1.security.AppwriteUsersClient;

@SpringBootTest
class AuthCodeCupApplicationTests {

    @MockBean
    private AppwriteSessionVerifier appwriteSessionVerifier;

    @MockBean
    private AppwriteUsersClient appwriteUsersClient;

    @Test
    void contextLoads() {
    }

}
