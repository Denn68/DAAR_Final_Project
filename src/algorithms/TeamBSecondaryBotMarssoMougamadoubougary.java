/* ******************************************************
 * Team B Secondary Bot - Kamikaze
 * Stratégie: Cherche ennemis, fonce dessus en tirant sans jamais s'arrêter
 *
 * Patch:
 *  - Au lieu d'envoyer (x,y) faux, envoie un hint fiable: direction + distance
 *  - Si plus d'ennemi en SUICIDE pendant N steps -> retour SEARCHING
 * ******************************************************/
package algorithms;

import robotsimulator.Brain;
import characteristics.Parameters;
import characteristics.IRadarResult;
import java.util.ArrayList;

public class TeamBSecondaryBotMarssoMougamadoubougary extends Brain {
    // États
    private static final int SEARCHING = 0;
    private static final int SUICIDE = 1;

    private int currentState;
    private double targetDirection;

    // Constantes
    private static final double HEADINGPRECISION = 0.01;
    private static final int FIRE_LATENCY = 20;
    private static final int BROADCAST_INTERVAL = 10;

    // Si ennemi "disparaît" en SUICIDE -> reset
    private static final int NO_ENEMY_RESET_STEPS = 60;

    private int fireCounter;

    // Position tracking (plus utilisé pour guider les mains)
    private static int botCounter = 0;
    private int myId;
    private int broadcastCooldown = 0;

    private int noEnemyCounter = 0;

    public TeamBSecondaryBotMarssoMougamadoubougary() { super(); }

    public void activate() {
        currentState = SEARCHING;
        fireCounter = 0;
        broadcastCooldown = 0;
        noEnemyCounter = 0;

        myId = botCounter++;
        sendLogMessage("Kamikaze B" + myId + " activé");
    }

    public void step() {
        if (fireCounter > 0) fireCounter--;
        if (broadcastCooldown > 0) broadcastCooldown--;

        switch (currentState) {
            case SEARCHING: stepSearching(); break;
            case SUICIDE:   stepSuicide();   break;
        }
    }

    private void stepSearching() {
        ArrayList<IRadarResult> radarResults = detectRadar();

        for (IRadarResult r : radarResults) {
            if (isEnemy(r.getObjectType())) {
                targetDirection = r.getObjectDirection();
                double dist = r.getObjectDistance();

                currentState = SUICIDE;
                noEnemyCounter = 0;

                // ✅ hint direction+distance
                broadcast("KAMIKAZE_HINT:" + myId + ":" + targetDirection + ":" + dist);
                broadcastCooldown = BROADCAST_INTERVAL;

                sendLogMessage("ENNEMI! SUICIDE + hint");
                return;
            }
        }

        // Avancer vers WEST en recherche
        if (!isHeading(Parameters.WEST)) turnToward(Parameters.WEST);
        else move();
    }

    private void stepSuicide() {
        ArrayList<IRadarResult> radarResults = detectRadar();
        IRadarResult closestEnemy = null;
        double closestDistance = Double.MAX_VALUE;

        for (IRadarResult r : radarResults) {
            if (isEnemy(r.getObjectType())) {
                if (r.getObjectDistance() < closestDistance) {
                    closestEnemy = r;
                    closestDistance = r.getObjectDistance();
                }
            }
        }

        if (closestEnemy != null) {
            targetDirection = closestEnemy.getObjectDirection();
            noEnemyCounter = 0;

            // broadcast hint régulièrement
            if (broadcastCooldown <= 0) {
                broadcast("KAMIKAZE_HINT:" + myId + ":" + targetDirection + ":" + closestDistance);
                broadcastCooldown = BROADCAST_INTERVAL;
            }
        } else {
            noEnemyCounter++;
            if (noEnemyCounter >= NO_ENEMY_RESET_STEPS) {
                currentState = SEARCHING;
                noEnemyCounter = 0;
                sendLogMessage("Plus d'ennemi -> SEARCHING");
                return;
            }
        }

        // Tir
        if (fireCounter == 0) {
            fire(targetDirection);
            fireCounter = FIRE_LATENCY;
        }

        // Fonce
        if (!isHeading(targetDirection)) turnToward(targetDirection);
        else move();
    }

    private boolean isEnemy(IRadarResult.Types type) {
        return type == IRadarResult.Types.OpponentMainBot ||
               type == IRadarResult.Types.OpponentSecondaryBot;
    }

    private boolean isHeading(double dir) {
        return Math.abs(Math.sin(getHeading() - dir)) < HEADINGPRECISION;
    }

    private void turnToward(double targetDir) {
        double diff = targetDir - getHeading();
        while (diff > Math.PI) diff -= 2 * Math.PI;
        while (diff < -Math.PI) diff += 2 * Math.PI;

        if (diff > 0) stepTurn(Parameters.Direction.RIGHT);
        else stepTurn(Parameters.Direction.LEFT);
    }
}
