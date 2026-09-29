package org.bsl.meetingroom.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bsl.meetingroom.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
public class TokenService {
    private final ObjectMapper mapper;
    private final byte[] secret;
    private final long expirationMinutes;
    private final Base64.Encoder encoder=Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder decoder=Base64.getUrlDecoder();

    public TokenService(ObjectMapper mapper,@Value("${app.jwt-secret}") String secret,@Value("${app.jwt-expiration-minutes:480}") long expirationMinutes){
        this.mapper=mapper; this.secret=secret.getBytes(StandardCharsets.UTF_8); this.expirationMinutes=expirationMinutes;
    }

    public String generate(User user){
        try{
            String header=encoder.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            Map<String,Object> claims=new LinkedHashMap<>();
            claims.put("sub",user.getUsername()); claims.put("uid",user.getId()); claims.put("role",user.getRole().name()); claims.put("ver",user.getAuthVersion());
            claims.put("exp",Instant.now().plusSeconds(expirationMinutes*60).getEpochSecond());
            String payload=encoder.encodeToString(mapper.writeValueAsBytes(claims));
            String unsigned=header+"."+payload;
            return unsigned+"."+encoder.encodeToString(sign(unsigned));
        }catch(Exception e){throw new IllegalStateException("Cannot generate token",e);}
    }

    public TokenClaims parse(String token){
        try{
            String[] parts=token.split("\\.");
            if(parts.length!=3) return null;
            byte[] expected=sign(parts[0]+"."+parts[1]);
            byte[] actual=decoder.decode(parts[2]);
            if(!java.security.MessageDigest.isEqual(expected,actual)) return null;
            Map<String,Object> map=mapper.readValue(decoder.decode(parts[1]),new TypeReference<>(){});
            long exp=((Number)map.get("exp")).longValue();
            if(Instant.now().getEpochSecond()>=exp) return null;
            long ver=map.get("ver") instanceof Number n?n.longValue():0L;
            return new TokenClaims(String.valueOf(map.get("sub")),String.valueOf(map.get("role")),ver);
        }catch(Exception e){return null;}
    }

    private byte[] sign(String value) throws Exception{
        Mac mac=Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret,"HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }
    public record TokenClaims(String username,String role,long authVersion){}
}
