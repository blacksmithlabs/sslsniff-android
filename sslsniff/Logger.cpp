/*
 * Copyright (c) 2002-2009 Moxie Marlinspike
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA
 */

#include "Logger.hpp"
#include <boost/format.hpp>

#include <log4cpp/Category.hh>
#include <log4cpp/FileAppender.hh>
#include <log4cpp/BasicLayout.hh>
#include <sstream>

std::string Logger::toAsciiHex(const char* buf, int len) {
  std::stringstream ss;

  int i, line_len;
  int offset = 0;
  int line_width = 16;
  int remaining = len;
  const char* ch;

  if (len <= 0)
    return "";

  for ( ;; ) {
    // Compute current line length
    line_len = (remaining < line_width) ? remaining : line_width;

    // Print the offset
    ss << (boost::format("%|05d|%1%") % offset) << "    ";

    // Print the hex for the line
    ch = buf + offset;
    for (i=0; i<line_len; i++) {
      ss << boost::format("%|02x|%1%") % *ch;
      ch++;
      // Print extra space after 4th byte for visual aid
      if (i > 0 && (i+1) % 4 == 0) {
        ss << " ";
      }
    }
    // Fill in any extra space in the line with spaces
    for (i=line_len; i<line_width; i++) {
      ss << "   ";
      // Print extra space after 4th byte for constant width
      if (i > 0 && (i+1) % 4 == 0) {
        ss << " ";
      }
    }
    // Gap before printable chars
    ss << "   ";

    // Printable chars
    ch = buf + offset;
    for (i=0; i<line_len; i++) {
      if (*ch > 0x20 && *ch < 0x7F) {
        ss << (char)*ch;
      } else {
        ss << ".";
      }
      // Print extra space after 4th byte for visual aid
      if (i > 0 && (i+1) % 4 == 0) {
        ss << " ";
      }
      ch++;
    }

    // End of line
    ss << "\n";

    // If we have less than a line left, we're done
    if (remaining < line_width) {
      break;
    }

    // Compute remaining
    remaining -= line_len;
    if (remaining <= 0) {
      break;
    }

    // Add to offset
    offset += line_width;
  }

  return ss.str();
}

void Logger::initialize(std::string &path, bool postOnly) {
  log4cpp::Appender* app  = new log4cpp::FileAppender("FileAppender", path);
  log4cpp::Layout* layout = new log4cpp::BasicLayout();
  app->setLayout(layout);

  log4cpp::Category &sslsniff = log4cpp::Category::getInstance("sslsniff");
  
  sslsniff.setAdditivity(false);
  sslsniff.setAppender(app);
  if (postOnly)
    sslsniff.setPriority(log4cpp::Priority::INFO);
  else
    sslsniff.setPriority(log4cpp::Priority::DEBUG);
}

void Logger::logFromServer(std::string &name, char *buf, int len) {
  std::string data = toAsciiHex(buf, len);
  std::string message = "Read from Server (";
  message.append(name);
  message.append(") :\n");
  message.append(data);

  log4cpp::Category::getInstance("sslsniff").debug(message);
}

void Logger::logFromClient(std::string &name, char* buf, int len) {
  std::string data = toAsciiHex(buf, len);
  std::string message = "Read from Client (";
  message.append(name);
  message.append(") :\n");
  message.append(data);

  log4cpp::Category::getInstance("sslsniff").debug(message);
}

void Logger::logFromClient(std::string &name, HttpHeaders &headers) {
  std::string message = "Got POST (";
  message.append(name);
  message.append(") :\n");
  message.append(headers.getPostData());

  log4cpp::Category::getInstance("sslsniff").info(message);
}

void Logger::logError(std::string error) {
  log4cpp::Category::getInstance("sslsniff").debug(error);
}

void Logger::logInit(std::string message) {
  log4cpp::Category::getInstance("sslsniff").info(message);
}
