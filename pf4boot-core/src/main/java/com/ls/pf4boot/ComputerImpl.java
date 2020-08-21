package com.ls.pf4boot;

import org.springframework.stereotype.Service;

/**
 * ComputerImpl
 *
 * @author yangzj
 * @version 1.0
 */
public class ComputerImpl implements Computer {
  @Override
  public double add(double n1, double n2) {
    return n1+n2;
  }
}
