package com.boots.repository;

import com.boots.repository.db_a.DCSet_A;
import lombok.extern.slf4j.Slf4j;

@Slf4j

public class CONST {

    private static CONST instance;
    boolean noGui = true;
    public int databaseSystem = -1;
    static public int MIN_MEMORY_TAIL = 1 << 20;

    boolean isStopping = false;

    DCSet_A dcSet_A;

    public synchronized static CONST getInstance() {
        if (instance == null) {
            instance = new CONST();
            instance.getInstance().isGuiEnabled();
        }

        return instance;
    }

    public boolean isGuiEnabled() {

        if (System.getProperty("nogui") != null) {
            return (noGui = false);
        }

        return noGui;
    }

    public String getDataChainPath() {
        return "/";
    }

    public void stopAndExit(int par) {

        // PREVENT MULTIPLE CALLS
        if (this.isStopping)
            return;
        this.isStopping = true;


        // CLOSE DATABABASE
        log.info("Closing database");
        this.dcSet_A.close();

        log.info("Closed.");
        // FORCE CLOSE
        if (par != -999999) {
            log.info("EXIT parameter:" + par);
            System.exit(par);
            //System.
            // bat
            // if %errorlevel% neq 0 exit /b %errorlevel%
        } else {
            log.info("EXIT parameter:" + 0);
        }
    }

}
