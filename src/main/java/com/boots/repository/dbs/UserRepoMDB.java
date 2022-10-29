package com.boots.repository.dbs;

import com.boots.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserRepoMDB extends MapDBRepository<Integer, User> {

    MapDBSet mapDB;

    public UserRepoMDB(MapDBSet mapDB) {
        super(mapDB, mapDB.getUsers());
    }

}
