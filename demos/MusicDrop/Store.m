//
//  Store.m
//  MusicDrop
//
//  Created by Sacha Arnoud on 3/24/06.
//  Copyright 2006 __MyCompanyName__. All rights reserved.
//

#import "Store.h"
#import "Id3Wrapper.h"

#include <unistd.h>
#include <stdio.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/uio.h>

#define NB_THREADS 5

#define ITUNES_ARTIST @"Artist"
#define ITUNES_ALBUM  @"Album"
#define ITUNES_TITLE  @"Name"
#define ITUNES_YEAR   @"Year"

@implementation Store

-(id)initIP:(NSString*)ip
   andFiles:(NSArray*)files
andCallback:(id)callbackP
{
  [clusterIP release];
  clusterIP = ip;
  [clusterIP retain];

  [fileEntries release];
  fileEntries = files;
  [fileEntries retain];
  currentIndex = 0;
  nbStored = 0;

  callback = (id<StoreCallback>)callbackP;

  done = [[NSConditionLock alloc] initWithCondition: 0];
  lock = [[NSLock alloc] init];

  return(self);
}

static long
file_reader(void *stream,
	    char *buf,
	    long n)
{
  int file = (int)stream;
  return( read(file, buf, n) );
}

-(void)doUpload:(id)arg
{
  NSAutoreleasePool *pool = [[NSAutoreleasePool alloc] init];
  
  [callback storeInit: [fileEntries count]];

  hcerr_t err = HCERR_OK;

  if (err == HCERR_OK) {
    int i;
    for (i=1; i<NB_THREADS; i++) {
      [NSThread detachNewThreadSelector:@selector(threadStart:)
                toTarget:self
                withObject: [[ThreadArg alloc] initWithNb: i] ];
    }
    [self threadStart: [[ThreadArg alloc] initWithNb: 0] ];
  }
  
  // Wait for the store to complete
  [done lockWhenCondition: 1];
  [done unlockWithCondition: 0];

  NSLog(@"All stores are complete");

  if (err == HCERR_OK) {
    [callback storeCompleted];
  }

  [pool release];
}

-(NSDictionary*)populateMetadata:(NSDictionary*)entry
			withFile:(NSString*)filename;

{
  Id3Wrapper *id3 = [[Id3Wrapper alloc] initWithFilename: filename];
  NSString *keys[4];
  id values[4];
  
  keys[0] = ITUNES_TITLE;
  values[0] = [entry valueForKey:ITUNES_TITLE];
  if (values[0] == nil) {
    NSLog(@"Getting title from ID3 tags");
    values[0] = [id3 getTitle];
  }

  keys[1] = ITUNES_ARTIST;
  values[1] = [entry valueForKey:ITUNES_ARTIST];
  if (values[1] == nil) {
    NSLog(@"Getting artist from ID3 tags");
    values[1] = [id3 getArtist];
  }

  keys[2] = ITUNES_ALBUM;
  values[2] = [entry valueForKey:ITUNES_ALBUM];
  if (values[2] == nil) {
    NSLog(@"Getting album from ID3 tags");
    values[2] = [id3 getAlbum];
  }

  keys[3] = ITUNES_YEAR;
  values[3] = [NSNumber numberWithLong: [id3 getYear]];

  [id3 release];

  NSArray *aKeys = [NSArray arrayWithObjects:keys
			    count: 4];
  NSArray *aValues = [NSArray arrayWithObjects:values
			      count: 4];
  NSDictionary *result = [NSDictionary dictionaryWithObjects: aValues
				       forKeys: aKeys];

  return(result);
}

-(void)threadStart:(ThreadArg*)arg
{
  NSAutoreleasePool *pool = nil;
  if ([arg nb]>0) {
    pool = [[NSAutoreleasePool alloc] init];
  }
  
  NSDictionary *entry = nil;
  hc_session_t *session = NULL;
  hcerr_t err;

  do {
    [lock lock];
    if (currentIndex < [fileEntries count]) {
      entry = (NSDictionary*)[fileEntries objectAtIndex: currentIndex];
    } else {
      entry = nil;
    }
    if (currentIndex <= [fileEntries count])
      currentIndex++;
    [lock unlock];

    if (entry != nil) {
      if (!session) {
        err = hc_session_create_ez((char*)[clusterIP cStringUsingEncoding: NSASCIIStringEncoding],
                                   8080,
                                   &session);
        if (err != HCERR_OK) {
          [lock lock];
          [callback storeFailed:@"hc_session_create_ez failed"];
          session = NULL;
          entry = nil;
          [lock unlock];
        }
      }
    }
    
    if (entry != nil) {
      NSURL *url = [[NSURL alloc] initWithString: [entry valueForKey: @"Location"]];
      NSString *filename = [url path];
      [url release];
    
      NSLog(@"[t%d] Uploading [%@]", 
            [arg nb], filename);

      NSDictionary *metadata = [self populateMetadata:entry
				     withFile: filename];

      // Upload the file
      hcerr_t localerr = HCERR_OK;

      int file = open([filename cStringUsingEncoding: NSUTF8StringEncoding],
                      O_RDONLY);
      if (file == -1) {
        NSLog(@"[t%d] Fqiled to open $@", 
              [arg nb], filename);
        localerr = HCERR_INIT_FAILED;
      }
      
      hc_nvr_t *md = NULL;

      if (localerr == HCERR_OK) {
        localerr = hc_nvr_create(session, 5, &md);
        if (localerr != HCERR_OK) {
          NSLog(@"[t%d] hc_nvr_create failed", [arg nb]);
          md = NULL;
        }
      }

      if (localerr == HCERR_OK) {
        localerr = hc_nvr_add_string(md, "mp3.title", (char*)[[metadata valueForKey:ITUNES_TITLE] UTF8String]);
        if (localerr != HCERR_OK)
          NSLog(@"Failed to add the title [%d]", localerr);
      }
      if (localerr == HCERR_OK) {
        localerr = hc_nvr_add_string(md, "mp3.artist", (char*)[[metadata valueForKey:ITUNES_ARTIST] cStringUsingEncoding: NSUTF8StringEncoding]);
        if (localerr != HCERR_OK)
          NSLog(@"Failed to add the artist");
      }
      if (localerr == HCERR_OK) {
        localerr = hc_nvr_add_string(md, "mp3.album", (char*)[[metadata valueForKey:ITUNES_ALBUM] cStringUsingEncoding: NSUTF8StringEncoding]);
        if (localerr != HCERR_OK)
          NSLog(@"Failed to add the album");
      }
      if (localerr == HCERR_OK) {
        localerr = hc_nvr_add_long(md, "mp3.date", [[metadata valueForKey:ITUNES_YEAR] longValue]);
        if (localerr != HCERR_OK)
          NSLog(@"Failed to add the date");
      }
      if (localerr == HCERR_OK) {
        localerr = hc_nvr_add_string(md, "mp3.type", "mp3");
        if (localerr != HCERR_OK)
          NSLog(@"Failed to add the type");
      }
	
      hc_system_record_t smd;

      if (localerr == HCERR_OK) {
        localerr = hc_store_both_ez(session, file_reader, (void*)file,
                                    md, &smd);
        if (localerr != HCERR_OK) {
          NSLog(@"[t%d] hc_store_both_ez failed [%d]",
                [arg nb], localerr);
        }
      }
      
      if (localerr == HCERR_OK) {
        NSLog(@"[t%d] %@ has been stored", [arg nb], filename);
      } else {
        NSLog(@"[t%d] Failed to store %@ [%d]",
              [arg nb], filename, localerr);
      }
      
      if (md) {
        hc_nvr_free(md);
        md = NULL;
      }
      if (file != -1) {
        close(file);
        file = -1;
      }

      NSString *oid = [NSString stringWithCString: smd.oid
                                encoding: NSASCIIStringEncoding];

      [lock lock];
      nbStored++;
      [callback storeStatus:nbStored
                outOf: [fileEntries count]
                withName: oid];
      if (nbStored == [fileEntries count]) {
        [done lockWhenCondition: 0];
        [done unlockWithCondition: 1];
      }
      [lock unlock];
    }
  } while (entry != nil);

  NSLog(@"[t%d] Exiting",
        [arg nb]);

  if (session) {
    hc_session_free(session);
    session = NULL;
  }

  [arg release];
  [pool release];
}

-(void)dealloc
{
  [clusterIP release];
  clusterIP = nil;

  [fileEntries release];
  fileEntries = nil;
  
  [super dealloc];
}

@end
