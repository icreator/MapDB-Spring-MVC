package com.boots.repository.dbs;

public interface Transacted {

    void commit();

    void rollback();
}

