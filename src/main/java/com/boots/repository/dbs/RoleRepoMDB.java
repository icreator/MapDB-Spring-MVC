package com.boots.repository.dbs;

import com.boots.entity.User;
import org.springframework.stereotype.Component;

@Component
public class RoleRepoMDB extends MapDBRepository<Integer, User> {

    MapDBSet mapDB;

    public RoleRepoMDB(MapDBSet mapDB) {
        super(mapDB, mapDB.getRoles());
    }

    @Override
    public User findByUsername(String username) {
        return map.get(username);
    }

}
