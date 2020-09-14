package no.knowit.rest;


import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import no.knowit.database.EmployeeRepository;
import no.knowit.database.UserRepository;
import no.knowit.database.entities.EmployeeEntity;
import no.knowit.database.entities.Role;
import no.knowit.database.entities.User;
import no.knowit.rest.dto.info.EmployeeInfo;
import no.knowit.rest.dto.info.EmployeeHireType;
import no.knowit.rest.dto.info.EmployeesOverallInfo;
import no.knowit.rest.dto.login.LoginInfo;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@RestController
@RequestMapping("rest")
@CrossOrigin(origins = "http://localhost:4200")
public class RestResource {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;

    public RestResource(UserRepository userRepository, EmployeeRepository employeeRepository) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
    }

    @PostMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LoginInfo> postLoginRequest(@RequestBody LoginInfo body) {
        List<User> userEnitites = userRepository.getByLogin(body.getPassword(), body.getUsername());

        if (userEnitites != null && !userEnitites.isEmpty()) {
        //    return ResponseEntity.ok(new LoginInfo().setToken(userEnitites.get(0).getPassKey())
       //             .setRole(userEnitites.get(0).getRole()));

            String token = createToken(userEnitites.get(0).getId(), userEnitites.get(0).getRole());
            LoginInfo loginInfo = new LoginInfo().setToken(token)
                    .setRole(userEnitites.get(0).getRole());
            return ResponseEntity.ok(loginInfo);
        }
        return ResponseEntity.status(401).build();
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE, path = "/employees")
    public ResponseEntity<EmployeesOverallInfo> getEmployees(@RequestHeader("token") String authToken) {
        DecodedJWT decodedJWT = validateAdminToken(checkTypeAndStripPreamble(authToken));
        if(decodedJWT == null) {
            return ResponseEntity.status(403).build();
        }
       Long id = decodedJWT.getClaim("id").asLong();
        Optional<User> userEnitites = userRepository.findById(id);
       if(!userEnitites.isPresent() || !userEnitites.get().getRole().equals(Role.ADMIN)) {
            return ResponseEntity.status(401).build();
        }

//        List<User> userEnitites = userRepository.getByPasskey(checkTypeAndStripPreamble(authToken));
//        if (userEnitites.isEmpty()) {
//            return ResponseEntity.status(403).build();
//        }
//        User user = userEnitites.get(0);

 //       if (user.getRole() != Role.ADMIN) {
 //           return ResponseEntity.status(401).build();
 //       }

        Iterable<EmployeeEntity> entities = employeeRepository.findAll();
        List<EmployeeInfo> employeeList = StreamSupport.stream(entities.spliterator(), false).map(this::mapToInfo).collect(Collectors.toList());
        EmployeesOverallInfo info = new EmployeesOverallInfo()
                .setEmployees(employeeList)
                .setTotal(employeeList.size())
                .setInvalid(employeeList.stream().filter(e -> e.getHireType() == EmployeeHireType.INVALID).count())
                .setValid(employeeList.stream().filter(e -> e.getHireType() == EmployeeHireType.HIRED).count())
                .setWarnings(employeeList.stream().filter(e -> e.getHireType() == EmployeeHireType.PROBATION).count());
        return ResponseEntity.ok(info);
    }

    @PostMapping(path = "/employees", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<EmployeeInfo>> registerInfo(@RequestBody EmployeeInfo employeeInfo, @RequestHeader("token") String authToken) {

        DecodedJWT decodedJWT = validateAdminToken(checkTypeAndStripPreamble(authToken));
        if(decodedJWT == null) {
            return ResponseEntity.status(403).build();
        }
        Long id = decodedJWT.getClaim("id").asLong();
        Optional<User> userEnitites = userRepository.findById(id);
        if(!userEnitites.isPresent() || !userEnitites.get().getRole().equals(Role.ADMIN)) {
            return ResponseEntity.status(401).build();
        }

//        List<User> userEnitites = userRepository.getByPasskey(checkTypeAndStripPreamble(authToken));
//        if (userEnitites.isEmpty()) {
        //          return ResponseEntity.status(403).build();
        //}
        //User user = userEnitites.get(0);
        //if (user.getRole() != Role.ADMIN) {
        //    return ResponseEntity.status(401).build();
        //}

        EmployeeEntity ent = mapFromInfo(employeeInfo);
        employeeRepository.save(ent);

        return ResponseEntity.ok(StreamSupport.stream(employeeRepository.findAll().spliterator(), false).map(this::mapToInfo).collect(Collectors.toList()));
    }

    @GetMapping
    public ResponseEntity<String> getStatus() {
        return ResponseEntity.ok("OK");
    }

    private EmployeeInfo mapToInfo(EmployeeEntity employeeEntity) {
        return new EmployeeInfo()
                .setId(employeeEntity.getId())
                .setCompany(employeeEntity.getCompany())
                .setFirstName(employeeEntity.getFirstName())
                .setLastName(employeeEntity.getLastName())
                .setHireType(employeeEntity.getHireType())
                .setBirthday(employeeEntity.getBirthday())
                .setWorkingRole(employeeEntity.getWorkingRole());
    }


    private EmployeeEntity mapFromInfo(EmployeeInfo employeeInfo) {
        return new EmployeeEntity()
                .setCompany(employeeInfo.getCompany())
                .setFirstName(employeeInfo.getFirstName())
                .setLastName(employeeInfo.getLastName())
                .setHireType(employeeInfo.getHireType())
                .setBirthday(employeeInfo.getBirthday())
                .setWorkingRole(employeeInfo.getWorkingRole());
    }

    private String checkTypeAndStripPreamble(String token) {
        if(token.matches("^Bearer .+")) {
            return token.replace("Bearer ", "");
        }
        return null;
    }

    private String createToken(long id, Role role) {
        try {
            Algorithm algorithm = Algorithm.HMAC256("secret");
            return JWT.create()
                    .withIssuer("test")
                    .withClaim("role", role.name())
                    .withClaim("id", id)
                    .sign(algorithm);
        } catch (JWTCreationException exception){
            return null;
        }
    }

    private DecodedJWT validateAdminToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256("secret");
            JWTVerifier verifier = JWT.require(algorithm)
                    .withIssuer("test")
                    .build();
            return verifier.verify(token);
        } catch (JWTCreationException exception){
            return null;
        }
    }
}
