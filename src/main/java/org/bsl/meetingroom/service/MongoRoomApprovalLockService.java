package org.bsl.meetingroom.service;

import org.bsl.meetingroom.model.RoomApprovalLock;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class MongoRoomApprovalLockService {
    private final MongoTemplate mongo;
    public MongoRoomApprovalLockService(MongoTemplate mongo){this.mongo=mongo;}

    public String tryAcquire(String roomId,Duration ttl){
        Instant now=Instant.now();
        String token=UUID.randomUUID().toString();
        Criteria available=new Criteria().orOperator(
                Criteria.where("lockedUntil").lt(now),
                Criteria.where("lockedUntil").exists(false),
                Criteria.where("token").is(null));
        Query query=new Query(new Criteria().andOperator(Criteria.where("_id").is(roomId),available));
        Update update=new Update().setOnInsert("_id",roomId).set("token",token).set("lockedUntil",now.plus(ttl));
        try{
            RoomApprovalLock lock=mongo.findAndModify(query,update,
                    FindAndModifyOptions.options().upsert(true).returnNew(true),RoomApprovalLock.class);
            return lock!=null && token.equals(lock.getToken())?token:null;
        }catch(DuplicateKeyException ex){
            return null;
        }
    }

    public void release(String roomId,String token){
        if(token==null)return;
        Query query=new Query(Criteria.where("_id").is(roomId).and("token").is(token));
        mongo.remove(query,RoomApprovalLock.class);
    }
}
