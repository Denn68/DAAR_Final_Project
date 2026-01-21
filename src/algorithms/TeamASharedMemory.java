package algorithms;

public class TeamASharedMemory {

    public static boolean enemyDetected = false;
    public static double enemyAngle = 0;
    public static double enemyDistance = 0;
    public static long lastDetectionTime = 0;

    public static void reportEnemy(double angle, double distance) {
        enemyDetected = true;
        enemyAngle = angle;
        enemyDistance = distance;
        lastDetectionTime = System.currentTimeMillis();
    }

    public static boolean isEnemyInfoValid() {
        return enemyDetected && (System.currentTimeMillis() - lastDetectionTime < 2000);
    }

    public static void clear() {
        enemyDetected = false;
    }
}
