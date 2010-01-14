#!/usr/bin/perl -w

use strict;
use CGI qw(:standard);

use qadb;

sub add_where($$)
{
  my ($query, $add) = @_;
  if (!defined($add)) {
      print start_html('Error');
      print 'add_where: add undef';
      print end_html;
      exit 1;
  }
  if (defined($query)) {
    return $query . ' and ' . $add;
  }
  return $add;
}

sub print_form()
{
  print p,
    start_form,
    " id ", textfield('test_id'),
    " test_name ", textfield('test_name'),
    br,
    " branch    ", textfield('branch'),
    " cluster   ", textfield('cluster'),
    br,
    " matrix ", textfield('matrix'),
    p,
    " format    ", radio_group(-name=>'format',
                                -values=>['short','long'],
                                -default=>'short'),
    br,
    submit,
    end_form;
}

################# main

  print header;

  my $where = undef;
  my $orderby = "id desc";
  my $test_id = undef;
  my $matrix = undef;
  my $format = 'short';
  my $short_url = '&format=long';
  my $period = undef;

  if (param('test_id')) {
      $test_id = param('test_id');
      $where = "id = " . $test_id;
  }
  my $p = param('test_name');
  if ($p) {
      $where = add_where($where, "test = \"$p\"");
      $short_url .= '&test_name=' . $p;
  }
  $p = param('cluster');
  if ($p) {
      $where = add_where($where, "cluster = \"$p\"");
      $short_url .= '&cluster=' . $p;
  }
  $p = param('branch');
  if ($p) {
      $where = add_where($where, "branch = \"$p\"");
      $short_url .= '&branch=' . $p;
  }
  $p = param('period');
  if ($p) {
      $short_url .= '&period=' . $p;
      if ($p =~ /today/) {
          $period = 'today';
          $where = add_where($where, 
		"end_time >= \"" . `date +"%F"` . " 00:01\"");
      } elsif ($p =~ /all/) {
          $period = 'all';
          $where = add_where($where, "end_time >= \"0001-01-01 00:01\"");
      } else {
          $where = add_where($where, "end_time >= \"" . $p . "\"");
      }
  }

  if (!defined($where)) {
      print start_html('Query');
      print_form();
      print end_html;
      exit 0;
  }
 
  $p = param('matrix');
  if ($p) {
      $short_url .= '&matrix=' . $p;
      $matrix = $p;
  }

  $p = param('format');
  if ($p) {
      $format = $p;
  }

  if (defined($test_id)) { 
      print start_html('Test Result: ' . $test_id);
  } elsif (defined($period)) {
      print start_html('Test Results (' . $period . ')');
  } else {
      print start_html('Test Results (' . $where . ')');
  }

  print_form();
  print hr;
  if (defined($period)) {
      print p, "query: (period=" . $period . "): ", b('where ' . $where), br;
  } else {
      print p, 'query: ', b('where ' . $where), br;
  }

  qadb::connect() or die "connect failed (${qadb::errno}): ${qadb::errstr}";
  my ($success, $rows) = qadb::test_result_query($where, $matrix, $orderby);

  if (!$success)
  {
    print hr, p, "Error (${qadb::errno}): ${qadb::errstr}", hr;
    print end_html;
    exit;
  }
  my @short_rows = ();
  if ($format =~ /short/) {
    my @short_headings = ('id','branch','test','pass','delta','cluster',
                    'start_time','end_time');
    @short_rows = th(\@short_headings);
  } else {
    print hr;
  }

  my $count = 0;
  foreach my $row (@$rows)
  {

    my ($id, $test, $pass, $cluster, $start_config, $end_config, $start_time, $end_time, $build, $branch, $performer, $proc_retval, $logs_url, $log_summary, $notes) = @$row;

    $count++;

    my $delta = undef;
    if (defined($branch))
    {
      my ($ok, $rows2) = qadb::prev_result($id, $test, $branch);
      if ($ok)
      {
        my $gotone = undef;
        foreach my $row2 (@$rows2)
        {
           $gotone = "yes";
           my ($o_id, $prev) = @$row2;

           if ( $pass == 1 )
           {
             if ( $prev == 1)
             {
               $delta = "==";
             }
             else
             {
               $delta = ":)";
             }
           }
           else
           {
             if ( $prev == 1)
             {
               $delta = ":(";
             }
             else
             {
               $delta = "==";
             }
           }
           last;
        }
        if (!defined($gotone))
        {
            if (defined($pass)) {
                if ( $pass == 1 ) {
                    $delta = ":)";
                } else {
                  $delta = ":(";
                }
            }
        }
      }
      else
      {
        print "Error prev_result() (${qadb::errno}): ${qadb::errstr}\n";
      }
    } else {
      $branch = '--';
    }
    $branch = '<center>' . $branch . '</center>';
    if (!defined($test)) {
      $test = '?';
    }
    if (!defined($pass)) {
      $pass = '?';
    }
    if (!defined($delta)) {
      $delta = "__";
    }
    if (!defined($cluster)) {
      $cluster = '__';
    }
    $cluster = '<center>' . $cluster . '</center>';
    if (!defined($start_time)) {
      $start_time = '__';
    }
    if (!defined($end_time)) {
      $start_time = '__';
    }
    my $tbl_id = undef;
    if ($format =~ /short/) {
      $tbl_id = "<a href=\"test_result.cgi?test_id=$id&$short_url\"><b>$id</b></a>";
    } else {
      $tbl_id = b($id);
    }
    my @values = ($tbl_id, $branch, $test, '<center>' . $pass . '</center>', 
			'<center>'.$delta . '</center>', $cluster, 
			$start_time, $end_time);

    if ($format =~ /short/) {
      push(@short_rows, td(\@values));
      next;
    }

    my @headings = ('id','branch','test','pass','delta','cluster',
                    'start_time','end_time');
    my @trows = th(\@headings);
    push(@trows,td(\@values));

    print p, table({-border=>undef,-width=>'100%'},
            Tr(\@trows)
           );

    print p, '<center>';
    @headings = ('name', 'value');
    @trows = th(\@headings);
    my $metric_rows = qadb::result_metric_query("result = ${id}");
    my $mc = 0;
    foreach my $metric_row (@$metric_rows)
    {
      $mc++;
      my ($name, $value) = @$metric_row;
      push(@trows, td([ $name, '<center>' . $value . '</center' ]));
    }

    if ($mc > 0) {
      print table({-border=>undef,-width=>'50%',-align=>'CENTER'},
            caption(b('Metrics')),
            Tr(\@trows)
           );
    }

    print p, h3('build'), pre($build), p;

    @headings = ('START', 'END');
    @trows = th(\@headings);
    @values = (pre($start_config), pre($end_config));
    push(@trows,td(\@values));

    print table({-border=>undef,-width=>'25%'},
            caption(b('Configuration')),
            Tr(\@trows)
           );


    my $bug_rows = qadb::result_bug_query("result = ${id}");
    my @bugs = ();
    foreach my $bug_row (@$bug_rows) {
      my ($result, $bug) = @$bug_row;
      push(@bugs, $bug);
    }
    print '</center>';

    if (defined($log_summary))
    {
        print p, h3('log summary'), pre($log_summary);
    }
    print hr;
  }
  if ($count == 0) {
    print p, 'no results';
  } else {
    if ($format =~ /short/) {
        print 'total=', $count, hr;
        print table({-border=>undef,-width=>'100%'}, Tr(\@short_rows));
    }
  }

  print end_html;
  qadb::disconnect() or die "disconnect failed (${qadb::errno}): ${qadb::errstr}";

