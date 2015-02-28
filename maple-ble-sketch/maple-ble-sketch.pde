
#include <stdint.h>
#include <stddef.h>
#include <string.h>

#define DEVICENAME "Sunrise"

#define WS2812_PIN_FIRST 1
#define WS2812_PIN_LAST 6

static void emit8(uint8_t v, int pin)
{
  volatile uint32_t cycles = 0;
  uint8_t mask = 0x80;
  gpio_dev *gpio_dev = PIN_MAP[pin].gpio_device;
  int gpio_pin = PIN_MAP[pin].gpio_bit;
  
  while (true)
  {
    if (v & mask)
    {
      gpio_toggle_bit(gpio_dev, gpio_pin);
      cycles++;
      cycles++;
      cycles++;
      cycles++;
      gpio_toggle_bit(gpio_dev, gpio_pin);
      cycles++;
      cycles++;
    } else {
      gpio_toggle_bit(gpio_dev, gpio_pin);
      cycles++;
      cycles++;
      gpio_toggle_bit(gpio_dev, gpio_pin);
      cycles++;
      cycles++;
      cycles++;
      cycles++;
    }
    
    if (mask == 0x01)
     break;
    mask >>= 1;
  }
}
  
static void emit24(uint32_t rgb, int pin)
{
  emit8((rgb >> 8) & 0xff, pin);
  emit8((rgb >> 16) & 0xff, pin);
  emit8(rgb & 0xff, pin);
}

#define NCOLS 6
#define NROWS 10

static uint32_t pixels[NCOLS][NROWS];
static uint32_t target_colours[NCOLS][NROWS];

#define COLOUR_OFF 0x000000

#define R(x) (((x) >> 16) & 0xff)
#define G(x) (((x) >> 8) & 0xff)
#define B(x) ((x) & 0xff)

#define PACK(r, g, b) (((r) << 16) | ((g) << 8) | (b))

static uint8_t blend_constant = 1;

static uint8_t blend8(uint8_t x, uint8_t y)
{
  int32_t x32 = x; /* signed to allow underflow */
  
  if (x > y)
  {
    return max(x32 - blend_constant, y);
  }
  
  if (x < y)
  {
    return min(x32 + blend_constant, y);
  }
  
  return x;
}

static uint32_t blend(uint32_t src, uint32_t dst)
{
  uint8_t r = blend8(R(src), R(dst));
  uint8_t g = blend8(G(src), G(dst));
  uint8_t b = blend8(B(src), B(dst));
  return PACK(r, g, b);
}

static void build_pixels(void)
{
  for (unsigned x = 0; x < NCOLS; x++)
    for (unsigned y = 0; y < NROWS; y++)
      if (pixels[x][y] != target_colours[x][y])
        pixels[x][y] = blend(pixels[x][y], target_colours[x][y]);
}

static void emit_pixels(int pin, const uint32_t *pixels, unsigned npixels)
{
  noInterrupts();
  for (unsigned i = 0; i < npixels; i++)
    emit24(pixels[i], pin);
  interrupts();
}

static void set_all_colours(uint32_t v)
{
  for (unsigned x = 0; x < NCOLS; x++)
    for (unsigned y = 0; y < NROWS; y++)
      target_colours[x][y] = v;
}

static void set_ring(unsigned y, uint32_t col)
{
  if (y >= NROWS)
    return;
  for (unsigned x = 0; x < NCOLS; x++)
      target_colours[x][y] = col;
}

static void set_column(unsigned x, uint32_t col)
{
  if (x >= NCOLS)
    return;
  for (unsigned y = 0; y < NROWS; y++)
      target_colours[x][y] = col;
}

static void set_pixel(unsigned x, unsigned y, uint32_t col)
{
  if (x >= NCOLS || y >= NROWS)
    return;
  target_colours[x][y] = col;
}

static void set_all_black(void)
{
  for (unsigned x = 0; x < NCOLS; x++)
  {
    for (unsigned y = 0; y < NROWS; y++)
    {
      target_colours[x][y] = COLOUR_OFF;
      pixels[x][y] = COLOUR_OFF;
    }
  }
}

static void rotate(void)
{
  for (unsigned y = 0; y < NROWS; y++)
  {
    uint32_t left = target_colours[0][y];
    
    for (unsigned x = 0; x < NCOLS - 1; x++)
    {
      target_colours[x][y] = target_colours[x + 1][y];
    }
    target_colours[NCOLS - 1][y] = left;
  }  
}

static void shift(void)
{
  for (unsigned x = 0; x < NCOLS; x++)
  {
    uint32_t top = target_colours[x][0];
    for (unsigned y = 0; y < NROWS - 1; y++)
    {
      target_colours[x][y] = target_colours[x][y + 1];
    }
    target_colours[x][NROWS - 1] = top;
  }  
}

static void update_leds(void)
{
  /* start at a known state */
  for (unsigned col = 0; col < NCOLS; col++)
    digitalWrite(WS2812_PIN_FIRST + col, LOW);
    
  build_pixels();
  for (unsigned col = 0; col < NCOLS; col++)
    emit_pixels(WS2812_PIN_FIRST + col, pixels[col], NROWS);
}

static int serial_transact(const char *send, const char *expect)
{
  int timeout = 1000;
  
  while (Serial1.available())
  {
    Serial1.read();
    Serial1.flush();
  }
  
  SerialUSB.print("Write ");
  SerialUSB.println(send);
  Serial1.print(send);
  
  SerialUSB.print("Expect ");
  SerialUSB.println(expect);
  
  SerialUSB.print("Read: '");
  
  while (*expect)
  {
    if (Serial1.available())
    {
      unsigned char in = Serial1.read();
      SerialUSB.print(in, BYTE);
      
      if (in != *expect)
      {
        SerialUSB.println("' ! unexpected !");
        Serial1.flush();
        return 1;
      }
      
      expect++;
    } else {
      delay(10);
      timeout -= 10;
      if (timeout == 0)
      {
        SerialUSB.println("' ! timeout !");
        Serial1.flush();
        return 1;
      }
    }
  }
       
  SerialUSB.println("' -> OK");
  return 0;
}

static int serial_read(uint8_t *out, size_t nout)
{
  int count = 0;
  int retryRead = 10;
  
  SerialUSB.print("r");
  
  while (nout && Serial1.available())
  {
    SerialUSB.print("b");
    *out = Serial1.read();
    SerialUSB.print("B");
    out++;
    nout--;
    count++;
    
    if (nout && !Serial1.available() && retryRead)
    {
      delay(5);
      retryRead--;
    }
    
    if (retryRead == 0)
      break;
  }
  
  SerialUSB.println("e");
  
  return count;
}

static int try_configure_device(void)
{
  return
    serial_transact("AT+NAME" DEVICENAME, "OK+Set:" DEVICENAME) ||
    serial_transact("AT+PIO11", "OK+Set:1") ||
    serial_transact("AT+NOTI1", "OK+Set:1");
}

static int check_config(void)
{
  if (serial_transact("AT+NAME?", "OK+NAME:" DEVICENAME))
    return try_configure_device();
  
  return 0;
}

static void read_command(void)
{
  uint8_t buffer[4], args[4];
  int used = serial_read(buffer, sizeof buffer);
  
  if (!used)
    return;
    
  SerialUSB.print("read '");
  SerialUSB.write(buffer, used);
  SerialUSB.println("'");
  
  if (used == 4 && memcmp(buffer, "OK+C", 4) == 0 &&
      3 == serial_read(args, 3) &&
      memcmp(args, "ONN", 3) == 0)
  {
    SerialUSB.println("Client connected");
  }
  
  if (used == 4 && memcmp(buffer, "OK+L", 4) == 0 &&
      3 == serial_read(args, 3) &&
      memcmp(args, "OST", 3) == 0)
  {
    SerialUSB.println("Client disconnected");
  }
  
  if (used == 4 && memcmp(buffer, "ring", 4) == 0 &&
      4 == serial_read(args, 4))
  {
    set_ring(args[0], PACK(args[1], args[2], args[3]));
  }
  
  if (used == 4 && memcmp(buffer, "clmn", 4) == 0 &&
      4 == serial_read(args, 4))
  {
    set_column(args[0], PACK(args[1], args[2], args[3]));
  }
  
  if (used == 4 && memcmp(buffer, "pix", 3) == 0 &&
      4 == serial_read(args, 4))
  {
    set_pixel(buffer[3], args[0],  PACK(args[1], args[2], args[3]));
  }
  
  if (used == 4 && memcmp(buffer, "bld", 3) == 0)
  {
    blend_constant = buffer[3];
  }
  
  if (used == 4 && memcmp(buffer, "blck", 4) == 0)
  {
    set_all_black();
  }
  
  if (used == 4 && memcmp(buffer, "rotr", 4) == 0)
  {
    rotate();
  }
  
  if (used == 4 && memcmp(buffer, "shft", 4) == 0)
  {
    shift();
  }
  
  Serial1.flush();
}

int counter = 0;

void setup()
{
  Serial1.begin(9600);
  pinMode(BOARD_LED_PIN, OUTPUT);
  for (int pin = WS2812_PIN_FIRST; pin <= WS2812_PIN_LAST; pin++)
    pinMode(pin, OUTPUT);
  
  delay(400);
  check_config();
    
  if (SerialUSB.isConnected() && (SerialUSB.getDTR() || SerialUSB.getRTS()))
  {
    // have a console
  } else {
    SerialUSB.end();
  }
}

void loop()
{
  toggleLED();
  read_command();
  update_leds();
  toggleLED();
  
  delay(50);
  counter++;
    
  if (counter == 10)
  {
#if 0
    uint32_t colour = PACK(random(0x00, 0xf), random(0x00, 0xf), random(0x00, 0xf));
    uint32_t instr = random(5);
    
    switch (instr)
    {
      case 0:
        set_ring(random(NROWS), colour);
        break;
      case 1:
        set_column(random(NCOLS), colour);
        break;
      case 2:
        rotate();
        break;
      case 3:
        shift();
        break;
    }
#endif
    counter = 0;
  }
}
