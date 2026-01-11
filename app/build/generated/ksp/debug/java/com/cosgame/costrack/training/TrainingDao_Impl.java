package com.cosgame.costrack.training;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class TrainingDao_Impl implements TrainingDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<TrainingSample> __insertionAdapterOfTrainingSample;

  private final Converters __converters = new Converters();

  private final EntityInsertionAdapter<TrainingSession> __insertionAdapterOfTrainingSession;

  private final EntityDeletionOrUpdateAdapter<TrainingSession> __updateAdapterOfTrainingSession;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAllSamples;

  private final SharedSQLiteStatement __preparedStmtOfDeleteSamplesByActivity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteSamplesBySession;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAllSessions;

  public TrainingDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfTrainingSample = new EntityInsertionAdapter<TrainingSample>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `training_samples` (`id`,`activityType`,`accX`,`accY`,`accZ`,`gyroX`,`gyroY`,`gyroZ`,`magnitude`,`sessionId`,`timestamp`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TrainingSample entity) {
        statement.bindLong(1, entity.getId());
        final String _tmp = __converters.fromActivityType(entity.getActivityType());
        statement.bindString(2, _tmp);
        statement.bindDouble(3, entity.getAccX());
        statement.bindDouble(4, entity.getAccY());
        statement.bindDouble(5, entity.getAccZ());
        statement.bindDouble(6, entity.getGyroX());
        statement.bindDouble(7, entity.getGyroY());
        statement.bindDouble(8, entity.getGyroZ());
        statement.bindDouble(9, entity.getMagnitude());
        statement.bindLong(10, entity.getSessionId());
        statement.bindLong(11, entity.getTimestamp());
      }
    };
    this.__insertionAdapterOfTrainingSession = new EntityInsertionAdapter<TrainingSession>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `training_sessions` (`id`,`activityType`,`startTime`,`endTime`,`samplesCount`,`missionDurationSeconds`,`completed`) VALUES (nullif(?, 0),?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TrainingSession entity) {
        statement.bindLong(1, entity.getId());
        final String _tmp = __converters.fromActivityType(entity.getActivityType());
        statement.bindString(2, _tmp);
        statement.bindLong(3, entity.getStartTime());
        statement.bindLong(4, entity.getEndTime());
        statement.bindLong(5, entity.getSamplesCount());
        statement.bindLong(6, entity.getMissionDurationSeconds());
        final int _tmp_1 = entity.getCompleted() ? 1 : 0;
        statement.bindLong(7, _tmp_1);
      }
    };
    this.__updateAdapterOfTrainingSession = new EntityDeletionOrUpdateAdapter<TrainingSession>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `training_sessions` SET `id` = ?,`activityType` = ?,`startTime` = ?,`endTime` = ?,`samplesCount` = ?,`missionDurationSeconds` = ?,`completed` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TrainingSession entity) {
        statement.bindLong(1, entity.getId());
        final String _tmp = __converters.fromActivityType(entity.getActivityType());
        statement.bindString(2, _tmp);
        statement.bindLong(3, entity.getStartTime());
        statement.bindLong(4, entity.getEndTime());
        statement.bindLong(5, entity.getSamplesCount());
        statement.bindLong(6, entity.getMissionDurationSeconds());
        final int _tmp_1 = entity.getCompleted() ? 1 : 0;
        statement.bindLong(7, _tmp_1);
        statement.bindLong(8, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteAllSamples = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM training_samples";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteSamplesByActivity = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM training_samples WHERE activityType = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteSamplesBySession = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM training_samples WHERE sessionId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAllSessions = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM training_sessions";
        return _query;
      }
    };
  }

  @Override
  public Object insertSample(final TrainingSample sample,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfTrainingSample.insertAndReturnId(sample);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertSamples(final List<TrainingSample> samples,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfTrainingSample.insert(samples);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertSession(final TrainingSession session,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfTrainingSession.insertAndReturnId(session);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateSession(final TrainingSession session,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfTrainingSession.handle(session);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAllSamples(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAllSamples.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteAllSamples.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteSamplesByActivity(final ActivityType activityType,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteSamplesByActivity.acquire();
        int _argIndex = 1;
        final String _tmp = __converters.fromActivityType(activityType);
        _stmt.bindString(_argIndex, _tmp);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteSamplesByActivity.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteSamplesBySession(final long sessionId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteSamplesBySession.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, sessionId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteSamplesBySession.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAllSessions(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAllSessions.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteAllSessions.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getSamplesByActivity(final ActivityType activityType,
      final Continuation<? super List<TrainingSample>> $completion) {
    final String _sql = "SELECT * FROM training_samples WHERE activityType = ? ORDER BY timestamp";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    final String _tmp = __converters.fromActivityType(activityType);
    _statement.bindString(_argIndex, _tmp);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<TrainingSample>>() {
      @Override
      @NonNull
      public List<TrainingSample> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfActivityType = CursorUtil.getColumnIndexOrThrow(_cursor, "activityType");
          final int _cursorIndexOfAccX = CursorUtil.getColumnIndexOrThrow(_cursor, "accX");
          final int _cursorIndexOfAccY = CursorUtil.getColumnIndexOrThrow(_cursor, "accY");
          final int _cursorIndexOfAccZ = CursorUtil.getColumnIndexOrThrow(_cursor, "accZ");
          final int _cursorIndexOfGyroX = CursorUtil.getColumnIndexOrThrow(_cursor, "gyroX");
          final int _cursorIndexOfGyroY = CursorUtil.getColumnIndexOrThrow(_cursor, "gyroY");
          final int _cursorIndexOfGyroZ = CursorUtil.getColumnIndexOrThrow(_cursor, "gyroZ");
          final int _cursorIndexOfMagnitude = CursorUtil.getColumnIndexOrThrow(_cursor, "magnitude");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final List<TrainingSample> _result = new ArrayList<TrainingSample>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TrainingSample _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final ActivityType _tmpActivityType;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfActivityType);
            _tmpActivityType = __converters.toActivityType(_tmp_1);
            final float _tmpAccX;
            _tmpAccX = _cursor.getFloat(_cursorIndexOfAccX);
            final float _tmpAccY;
            _tmpAccY = _cursor.getFloat(_cursorIndexOfAccY);
            final float _tmpAccZ;
            _tmpAccZ = _cursor.getFloat(_cursorIndexOfAccZ);
            final float _tmpGyroX;
            _tmpGyroX = _cursor.getFloat(_cursorIndexOfGyroX);
            final float _tmpGyroY;
            _tmpGyroY = _cursor.getFloat(_cursorIndexOfGyroY);
            final float _tmpGyroZ;
            _tmpGyroZ = _cursor.getFloat(_cursorIndexOfGyroZ);
            final float _tmpMagnitude;
            _tmpMagnitude = _cursor.getFloat(_cursorIndexOfMagnitude);
            final long _tmpSessionId;
            _tmpSessionId = _cursor.getLong(_cursorIndexOfSessionId);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            _item = new TrainingSample(_tmpId,_tmpActivityType,_tmpAccX,_tmpAccY,_tmpAccZ,_tmpGyroX,_tmpGyroY,_tmpGyroZ,_tmpMagnitude,_tmpSessionId,_tmpTimestamp);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getAllSamples(final Continuation<? super List<TrainingSample>> $completion) {
    final String _sql = "SELECT * FROM training_samples ORDER BY timestamp";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<TrainingSample>>() {
      @Override
      @NonNull
      public List<TrainingSample> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfActivityType = CursorUtil.getColumnIndexOrThrow(_cursor, "activityType");
          final int _cursorIndexOfAccX = CursorUtil.getColumnIndexOrThrow(_cursor, "accX");
          final int _cursorIndexOfAccY = CursorUtil.getColumnIndexOrThrow(_cursor, "accY");
          final int _cursorIndexOfAccZ = CursorUtil.getColumnIndexOrThrow(_cursor, "accZ");
          final int _cursorIndexOfGyroX = CursorUtil.getColumnIndexOrThrow(_cursor, "gyroX");
          final int _cursorIndexOfGyroY = CursorUtil.getColumnIndexOrThrow(_cursor, "gyroY");
          final int _cursorIndexOfGyroZ = CursorUtil.getColumnIndexOrThrow(_cursor, "gyroZ");
          final int _cursorIndexOfMagnitude = CursorUtil.getColumnIndexOrThrow(_cursor, "magnitude");
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final List<TrainingSample> _result = new ArrayList<TrainingSample>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TrainingSample _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final ActivityType _tmpActivityType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfActivityType);
            _tmpActivityType = __converters.toActivityType(_tmp);
            final float _tmpAccX;
            _tmpAccX = _cursor.getFloat(_cursorIndexOfAccX);
            final float _tmpAccY;
            _tmpAccY = _cursor.getFloat(_cursorIndexOfAccY);
            final float _tmpAccZ;
            _tmpAccZ = _cursor.getFloat(_cursorIndexOfAccZ);
            final float _tmpGyroX;
            _tmpGyroX = _cursor.getFloat(_cursorIndexOfGyroX);
            final float _tmpGyroY;
            _tmpGyroY = _cursor.getFloat(_cursorIndexOfGyroY);
            final float _tmpGyroZ;
            _tmpGyroZ = _cursor.getFloat(_cursorIndexOfGyroZ);
            final float _tmpMagnitude;
            _tmpMagnitude = _cursor.getFloat(_cursorIndexOfMagnitude);
            final long _tmpSessionId;
            _tmpSessionId = _cursor.getLong(_cursorIndexOfSessionId);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            _item = new TrainingSample(_tmpId,_tmpActivityType,_tmpAccX,_tmpAccY,_tmpAccZ,_tmpGyroX,_tmpGyroY,_tmpGyroZ,_tmpMagnitude,_tmpSessionId,_tmpTimestamp);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getCountByActivity(final ActivityType activityType,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM training_samples WHERE activityType = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    final String _tmp = __converters.fromActivityType(activityType);
    _statement.bindString(_argIndex, _tmp);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(0);
            _result = _tmp_1;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<Integer> getCountByActivityFlow(final ActivityType activityType) {
    final String _sql = "SELECT COUNT(*) FROM training_samples WHERE activityType = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    final String _tmp = __converters.fromActivityType(activityType);
    _statement.bindString(_argIndex, _tmp);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"training_samples"}, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(0);
            _result = _tmp_1;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getTotalSamplesCount(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM training_samples";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<Integer> getTotalSamplesCountFlow() {
    final String _sql = "SELECT COUNT(*) FROM training_samples";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"training_samples"}, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getSessionsByActivity(final ActivityType activityType,
      final Continuation<? super List<TrainingSession>> $completion) {
    final String _sql = "SELECT * FROM training_sessions WHERE activityType = ? ORDER BY startTime DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    final String _tmp = __converters.fromActivityType(activityType);
    _statement.bindString(_argIndex, _tmp);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<TrainingSession>>() {
      @Override
      @NonNull
      public List<TrainingSession> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfActivityType = CursorUtil.getColumnIndexOrThrow(_cursor, "activityType");
          final int _cursorIndexOfStartTime = CursorUtil.getColumnIndexOrThrow(_cursor, "startTime");
          final int _cursorIndexOfEndTime = CursorUtil.getColumnIndexOrThrow(_cursor, "endTime");
          final int _cursorIndexOfSamplesCount = CursorUtil.getColumnIndexOrThrow(_cursor, "samplesCount");
          final int _cursorIndexOfMissionDurationSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "missionDurationSeconds");
          final int _cursorIndexOfCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "completed");
          final List<TrainingSession> _result = new ArrayList<TrainingSession>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TrainingSession _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final ActivityType _tmpActivityType;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfActivityType);
            _tmpActivityType = __converters.toActivityType(_tmp_1);
            final long _tmpStartTime;
            _tmpStartTime = _cursor.getLong(_cursorIndexOfStartTime);
            final long _tmpEndTime;
            _tmpEndTime = _cursor.getLong(_cursorIndexOfEndTime);
            final int _tmpSamplesCount;
            _tmpSamplesCount = _cursor.getInt(_cursorIndexOfSamplesCount);
            final int _tmpMissionDurationSeconds;
            _tmpMissionDurationSeconds = _cursor.getInt(_cursorIndexOfMissionDurationSeconds);
            final boolean _tmpCompleted;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfCompleted);
            _tmpCompleted = _tmp_2 != 0;
            _item = new TrainingSession(_tmpId,_tmpActivityType,_tmpStartTime,_tmpEndTime,_tmpSamplesCount,_tmpMissionDurationSeconds,_tmpCompleted);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getAllSessions(final Continuation<? super List<TrainingSession>> $completion) {
    final String _sql = "SELECT * FROM training_sessions ORDER BY startTime DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<TrainingSession>>() {
      @Override
      @NonNull
      public List<TrainingSession> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfActivityType = CursorUtil.getColumnIndexOrThrow(_cursor, "activityType");
          final int _cursorIndexOfStartTime = CursorUtil.getColumnIndexOrThrow(_cursor, "startTime");
          final int _cursorIndexOfEndTime = CursorUtil.getColumnIndexOrThrow(_cursor, "endTime");
          final int _cursorIndexOfSamplesCount = CursorUtil.getColumnIndexOrThrow(_cursor, "samplesCount");
          final int _cursorIndexOfMissionDurationSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "missionDurationSeconds");
          final int _cursorIndexOfCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "completed");
          final List<TrainingSession> _result = new ArrayList<TrainingSession>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TrainingSession _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final ActivityType _tmpActivityType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfActivityType);
            _tmpActivityType = __converters.toActivityType(_tmp);
            final long _tmpStartTime;
            _tmpStartTime = _cursor.getLong(_cursorIndexOfStartTime);
            final long _tmpEndTime;
            _tmpEndTime = _cursor.getLong(_cursorIndexOfEndTime);
            final int _tmpSamplesCount;
            _tmpSamplesCount = _cursor.getInt(_cursorIndexOfSamplesCount);
            final int _tmpMissionDurationSeconds;
            _tmpMissionDurationSeconds = _cursor.getInt(_cursorIndexOfMissionDurationSeconds);
            final boolean _tmpCompleted;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfCompleted);
            _tmpCompleted = _tmp_1 != 0;
            _item = new TrainingSession(_tmpId,_tmpActivityType,_tmpStartTime,_tmpEndTime,_tmpSamplesCount,_tmpMissionDurationSeconds,_tmpCompleted);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getCompletedSessions(
      final Continuation<? super List<TrainingSession>> $completion) {
    final String _sql = "SELECT * FROM training_sessions WHERE completed = 1 ORDER BY startTime DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<TrainingSession>>() {
      @Override
      @NonNull
      public List<TrainingSession> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfActivityType = CursorUtil.getColumnIndexOrThrow(_cursor, "activityType");
          final int _cursorIndexOfStartTime = CursorUtil.getColumnIndexOrThrow(_cursor, "startTime");
          final int _cursorIndexOfEndTime = CursorUtil.getColumnIndexOrThrow(_cursor, "endTime");
          final int _cursorIndexOfSamplesCount = CursorUtil.getColumnIndexOrThrow(_cursor, "samplesCount");
          final int _cursorIndexOfMissionDurationSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "missionDurationSeconds");
          final int _cursorIndexOfCompleted = CursorUtil.getColumnIndexOrThrow(_cursor, "completed");
          final List<TrainingSession> _result = new ArrayList<TrainingSession>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TrainingSession _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final ActivityType _tmpActivityType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfActivityType);
            _tmpActivityType = __converters.toActivityType(_tmp);
            final long _tmpStartTime;
            _tmpStartTime = _cursor.getLong(_cursorIndexOfStartTime);
            final long _tmpEndTime;
            _tmpEndTime = _cursor.getLong(_cursorIndexOfEndTime);
            final int _tmpSamplesCount;
            _tmpSamplesCount = _cursor.getInt(_cursorIndexOfSamplesCount);
            final int _tmpMissionDurationSeconds;
            _tmpMissionDurationSeconds = _cursor.getInt(_cursorIndexOfMissionDurationSeconds);
            final boolean _tmpCompleted;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfCompleted);
            _tmpCompleted = _tmp_1 != 0;
            _item = new TrainingSession(_tmpId,_tmpActivityType,_tmpStartTime,_tmpEndTime,_tmpSamplesCount,_tmpMissionDurationSeconds,_tmpCompleted);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getCompletedCountByActivity(final ActivityType activityType,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM training_sessions WHERE activityType = ? AND completed = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    final String _tmp = __converters.fromActivityType(activityType);
    _statement.bindString(_argIndex, _tmp);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(0);
            _result = _tmp_1;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<Integer> getCompletedCountByActivityFlow(final ActivityType activityType) {
    final String _sql = "SELECT COUNT(*) FROM training_sessions WHERE activityType = ? AND completed = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    final String _tmp = __converters.fromActivityType(activityType);
    _statement.bindString(_argIndex, _tmp);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"training_sessions"}, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(0);
            _result = _tmp_1;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getSampleCountsPerActivity(
      final Continuation<? super List<ActivitySampleCount>> $completion) {
    final String _sql = "\n"
            + "        SELECT activityType, COUNT(*) as count\n"
            + "        FROM training_samples\n"
            + "        GROUP BY activityType\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ActivitySampleCount>>() {
      @Override
      @NonNull
      public List<ActivitySampleCount> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfActivityType = 0;
          final int _cursorIndexOfCount = 1;
          final List<ActivitySampleCount> _result = new ArrayList<ActivitySampleCount>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ActivitySampleCount _item;
            final ActivityType _tmpActivityType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfActivityType);
            _tmpActivityType = __converters.toActivityType(_tmp);
            final int _tmpCount;
            _tmpCount = _cursor.getInt(_cursorIndexOfCount);
            _item = new ActivitySampleCount(_tmpActivityType,_tmpCount);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<ActivitySampleCount>> getSampleCountsPerActivityFlow() {
    final String _sql = "\n"
            + "        SELECT activityType, COUNT(*) as count\n"
            + "        FROM training_samples\n"
            + "        GROUP BY activityType\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"training_samples"}, new Callable<List<ActivitySampleCount>>() {
      @Override
      @NonNull
      public List<ActivitySampleCount> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfActivityType = 0;
          final int _cursorIndexOfCount = 1;
          final List<ActivitySampleCount> _result = new ArrayList<ActivitySampleCount>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ActivitySampleCount _item;
            final ActivityType _tmpActivityType;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfActivityType);
            _tmpActivityType = __converters.toActivityType(_tmp);
            final int _tmpCount;
            _tmpCount = _cursor.getInt(_cursorIndexOfCount);
            _item = new ActivitySampleCount(_tmpActivityType,_tmpCount);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
