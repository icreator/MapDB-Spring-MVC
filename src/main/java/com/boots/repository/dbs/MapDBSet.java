package com.boots.repository.dbs;

import com.boots.entity.User;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentMap;

@Component
public class MapDBSet {

    DB db;
    ConcurrentMap<Integer, User> users;
    ConcurrentMap<Integer, User> roles;

    public MapDBSet() {
        User user;
        db = DBMaker.memoryDB().make();
        users = (ConcurrentMap<Integer, User>) db.treeMap("users").createOrOpen();
        roles = (ConcurrentMap<Integer, User>) db.treeMap("roles").createOrOpen();

        if (roles.isEmpty()) {
            //roles.put("ROLE_ADMIN");
            //roles.put("ROLE_USER");
        }

        if (users.size() == 0) {
            users.put(new User("admin"));
        }
    }

    public ConcurrentMap getUsers() {
        return users;
    }
    public ConcurrentMap getRoles() {
        return roles;
    }

}
