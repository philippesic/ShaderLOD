package com.pp.shaderlod.dev.measurement;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

public final class OpenGlQueries implements GpuCollector.Queries {
  @Override
  public int create() {
    return GL15.glGenQueries();
  }

  @Override
  public void timestamp(int query) {
    GL33.glQueryCounter(query, GL33.GL_TIMESTAMP);
  }

  @Override
  public boolean available(int query) {
    return GL15.glGetQueryObjecti(query, GL15.GL_QUERY_RESULT_AVAILABLE) != 0;
  }

  @Override
  public long value(int query) {
    return GL33.glGetQueryObjectui64(query, GL15.GL_QUERY_RESULT);
  }

  @Override
  public void delete(int query) {
    GL15.glDeleteQueries(query);
  }
}
