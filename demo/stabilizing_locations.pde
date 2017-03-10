final int TERRIBLE = 0;
final int BAD = 1;
final int GOOD = 2;
final int BEST = 3;

final int MAX_LOCATION_COUNT = 10;

final int SPEED_THRESHOLD = 50;
final int SPEED_TIME_CLUE = 10000;// 10 sec
final int SPEED_CHECKABLE_COUNT = 3;

final int GROUPING_DISTANCE_THRESHOLD = 100;
final int GROUPING_MINIMUM_COUNT = 3;
final int GROUPING_ASSUME_BAD_COUNT = 3;

final int DISTANCE_THRESHOLD = 300;
final int DISTANCE_TIME_CLUE = 10;

final color TERRIBLE_COLOR = #880E4F;
final color BAD_COLOR = #F44336;
final color GOOD_COLOR = #40C4FF;
final color BEST_COLOR = #00E676;
final color TEXT_COLOR = #404040;
final color SELECTED_COLOR = #03A9F4;

ArrayList<MyLocation> stableList;
ArrayList<MyLocation> locationList;
ArrayList<ArrayList<MyLocation>> regionList;
int newLevel = GOOD;

void setup() {

  size(600, 800);
  //pixelDensity(displayDensity());
  ellipseMode(CENTER);
  rectMode(CENTER);
  frameRate(10);

  regionList = new ArrayList<ArrayList<MyLocation>>();
  locationList = new ArrayList<MyLocation>();
  stableList = new ArrayList<MyLocation>();
}

void mousePressed() {
  addNewMyLocation(mouseX, mouseY, newLevel);
}

void keyPressed() {
  if (key == 'r') {
    locationList.clear();
    stableList.clear();
    regionList.clear();
    println("reset");
  }

  int value = int(key) - 49;
  println(value);
  if (value >= TERRIBLE && value <= BEST ) {
    newLevel = value;
  }
}


color levelColor(int level) {
  switch(level) {
  case TERRIBLE: 
    return TERRIBLE_COLOR;
  case BAD: 
    return BAD_COLOR;
  case GOOD: 
    return GOOD_COLOR;
  case BEST: 
    return BEST_COLOR;
  }
  return #000000;
}


void draw() {

  background(255);

  // Guide 
  fill(TEXT_COLOR);
  textSize(12);
  if (newLevel == TERRIBLE) fill(SELECTED_COLOR); 
  else fill(TEXT_COLOR); 
  text("1 : TERRIBLE accuracy", 10, 20);
  if (newLevel == BAD) fill(SELECTED_COLOR); 
  else fill(TEXT_COLOR);
  text("2 : BAD accuracy", 10, 35);
  if (newLevel == GOOD) fill(SELECTED_COLOR); 
  else fill(TEXT_COLOR);
  text("3 : GOOD accuracy", 10, 50);
  if (newLevel == BEST) fill(SELECTED_COLOR); 
  else fill(TEXT_COLOR);
  text("4 : BEST accuracy", 10, 65);
  fill(TEXT_COLOR);
  text("r : reset", 10, 80);

  text("locationList.size()=" + locationList.size(), 300, 20);
  text("regionList.size()=" + regionList.size(), 300, 40);
  text("Distance Threshold", 300, 60);
  text("Used Location", 300, 80);
  fill(10, 30);
  noStroke();
  ellipse(290, 55, 30, 30);
  fill(BEST_COLOR);
  triangle(290-10, 70+10, 290, 70-8, 290+10, 70+10);


  // draw points
  int plen = locationList.size();

  for (int i=0; i<plen; i++) {
    MyLocation p = locationList.get(i);
    noStroke();
    fill(levelColor(p.level));
    if (i == plen - 1) {
      rect(p.x, p.y, 20, 20);
    } else {
      rect(p.x, p.y, 6, 6);
    }
    fill(10, 30);
    ellipse(p.x, p.y, GROUPING_DISTANCE_THRESHOLD, GROUPING_DISTANCE_THRESHOLD);

    if (i>0) {
      stroke(50, 100);
      line(locationList.get(i-1).x, locationList.get(i-1).y, p.x, p.y);
      textSize(10);
      if (p.speed < SPEED_THRESHOLD) {
        fill(50, 50, 200);
      } else {
        fill(200, 50, 0);
      }
      text("speed:" + p.speed, p.x, p.y + 10);
    }
  }

  // draw regionList
  int glen = regionList.size();
  for (int i=0; i<glen; i++) {
    ArrayList<MyLocation> group = regionList.get(i); 
    int regionListize = group.size();
    //fill(255, 204, 0, 40);
    fill(random(100, 255), 40);
    noStroke();
    beginShape();
    for (int j=0; j<regionListize; j++) {
      if (group.get(j).level > TERRIBLE) {
        vertex(group.get(j).x, group.get(j).y);
      }
    }
    endShape(CLOSE);
  }

  // draw stable mark
  int slen = stableList.size();
  for (int i=0; i<slen; i++) {
    noStroke();
    fill(BEST_COLOR);
    MyLocation p = stableList.get(i);
    triangle(p.x-10, p.y+10, p.x, p.y-8, p.x+10, p.y+10);
  }
}

void addNewMyLocation(int px, int py, int level) {
  MyLocation p;
  if (locationList.size() < 1) {
    p = new MyLocation(px, py, millis());
  } else {
    int addTime = 1000;//(int)random(1000, 5000);
    p = new MyLocation(px, py, locationList.get(locationList.size()-1).time + addTime);
  }

  p.level = level;
  println("New position", p.x, p.y, p.time, p.level);

  int t = millis();
  checkLocationLevel(p);
  println("stabilize time=", millis() - t);
}


void checkLocationLevel(MyLocation income) {

  if (income.level == TERRIBLE || income.level == BEST) {
    // obvious level
  } 
  else if (locationList.size() + 1 >= SPEED_CHECKABLE_COUNT) {
    // ## check level by speed
    MyLocation last = null;
    int plen = locationList.size();  
    for (int i=plen-1; i>=0; i--) {
      if (locationList.get(i).level > TERRIBLE) {
        last = locationList.get(i);
        break;
      }
    }

    if (last != null) { 
      // if income data is within speed checkable time 
      if (income.speedComparable(last)) {
        income.speed = income.speedFrom(last);
        if (income.speed >= 0 && income.speed < SPEED_THRESHOLD) {
          income.level = GOOD;
        } else {
          income.level = BAD;
        }
      } else {
        income.level = BAD;
      }
    }
  } // <-- speed checking


  // # Add new position to history
  locationList.add(income);
  if (locationList.size() > MAX_LOCATION_COUNT) { // Limit size
    locationList.remove(0);
  }


  // # Check Region
  checkLocationRegion();



  // # Stable position mark
  // We can not disgard the first usable location,
  // because the orders around me have to be shown as soon as. 
  if (/*locationList.size() > SPEED_CHECKABLE_COUNT && */income.level > BAD) {
    stableList.add(income.clone());
  }
  if (stableList.size() > MAX_LOCATION_COUNT) { // Limit size
    stableList.remove(0);
  }
}


void checkLocationRegion() {
  // ## start grouping
  regionList.clear();
  if (locationList.size() >= GROUPING_MINIMUM_COUNT) {
    // first group
    ArrayList<MyLocation> g = new ArrayList<MyLocation>();
    g.add(locationList.get(0));
    regionList.add(g);

    int loLength = locationList.size();
    for (int i=1; i < loLength; i++) {
      MyLocation next = locationList.get(i);
      //println("------- start inserting position=", i);

      boolean added = false;
      // check affordance
      for (int j=0; j<regionList.size(); j++) {  

        ArrayList<MyLocation> group = regionList.get(j);
        int groupLength = group.size();
        for (int k=groupLength - 1; k >= 0; k--) {
          MyLocation m = group.get(k);
          // if any location of group is within the distance driver can go,
          // we think next location is same group.
          if (next.distanceFrom(m) < GROUPING_DISTANCE_THRESHOLD) {
            group.add(next);
            added = true;
            //println("affordance group=", j, "distance=", s);
            break;
          }
        }

        if (added) {
          break;
        }
      } // check affordance

      if (!added) {
        // New group
        g = new ArrayList<MyLocation>();
        g.add(next);
        regionList.add(g);
        //println("new affordance group=", regionList.size()-1);
      }
    } // grouping

    // find big size
    //int gMaxSize = 0;
    int gLength = regionList.size();
    //for (int i=0; i<gLength; i++) {
    //  int size = regionList.get(i).size();
    //  if (size > gMaxSize) {
    //    gMaxSize = size;
    //  }
    //}

    // find big regionList and switch level between GOOD and BAD
    for (int i=0; i<gLength; i++) {
      ArrayList<MyLocation> group = regionList.get(i);
      int regionListize = group.size();
      //if (regionListize >= gMaxSize) {
      //  for (int j=0; j<regionListize; j++) {
      //    if (group.get(j).level == BAD) group.get(j).level = GOOD;
      //  }
      //} 
      //else 
      if (regionListize < GROUPING_ASSUME_BAD_COUNT) { // If the group has less than N locations, we assume it is bad group.
        for (int j=0; j<regionListize; j++) {
          if (group.get(j).level == GOOD) group.get(j).level = BAD;
        }
      }
    }
  }
}



class MyLocation {

  int x;
  int y;
  long time;
  float speed = 0;
  int level = 0; // BAD

  MyLocation(int x, int y, long time) {
    this.x = x;
    this.y = y;
    this.time = time;
  }

  float distanceFrom(MyLocation target) {
    return sqrt(pow(target.x - this.x, 2) + pow(target.y - this.y, 2));
  }

  float speedFrom(MyLocation old) {
    float distance = distanceFrom(old);
    long interval = this.time - old.time;
    float sec = interval / 1000f;

    return distance / sec;
  }

  boolean speedComparable(MyLocation old) {
    return (this.time - old.time < SPEED_TIME_CLUE) && (this.time > old.time);
  }

  MyLocation clone() {
    MyLocation p = new MyLocation(this.x, this.y, this.time);
    p.speed = this.speed;
    p.level = this.level;
    return p;
  }
}